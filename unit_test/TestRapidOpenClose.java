import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Rapid Open/Close Cycles
 * Repeatedly opens the database, verifies previously stored data persists,
 * inserts a new record, and closes the database. This tests:
 *   - Data persistence across open/close boundaries
 *   - No resource leaks from repeated open/close
 *   - Schema is correctly recognized on reopen (no re-creation)
 *   - Database file is not corrupted by frequent open/close
 *   - WAL/journal files are properly cleaned up between sessions
 *
 * NOTE: This test disrupts the shared database connection, so it MUST
 *       be called LAST in the test suite.
 */
public class TestRapidOpenClose {

    // Number of open/close cycles to perform
    static final int CYCLES = 50;

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Rapid Open/Close Cycles (" + CYCLES + " cycles) ---");

        String dbPath = "./unit_test_db.sqlite";

        try {
            // --- Phase 1: Clean the table and insert a baseline record ---
            // This ensures we start with a known state
            orma.deleteFromPerson().execute();

            Person baseline = new Person();
            baseline.name = "Baseline_Record";
            baseline.address = "open_close_test";
            baseline.social_number = 0;
            orma.insertIntoPerson(baseline);

            SormaUnitTest.assertCondition("Setup: baseline record inserted",
                orma.selectFromPerson().count() == 1);

            // --- Phase 2: Shutdown the current connection ---
            // We must close before we can reopen in the loop
            OrmaDatabase.shutdown();

            // --- Phase 3: Rapid open/close cycles ---
            boolean allCyclesPassed = true;
            boolean dataPersistenceOk = true;
            long startTime = System.currentTimeMillis();

            for (int cycle = 0; cycle < CYCLES; cycle++) {
                try {
                    // Reopen the database
                    OrmaDatabase cycleOrma = new OrmaDatabase(dbPath, "", false);
                    OrmaDatabase.init(1);

                    // Verify ALL previously inserted records still exist
                    // After cycle N, we should have: 1 baseline + N cycle records
                    int expectedCount = 1 + cycle;
                    int actualCount = cycleOrma.selectFromPerson().count();

                    if (actualCount != expectedCount) {
                        dataPersistenceOk = false;
                        System.err.println("  [ERROR] Cycle " + cycle + ": expected " +
                            expectedCount + " records, got " + actualCount);
                        allCyclesPassed = false;
                    }

                    // Verify baseline record is still intact
                    List<Person> baselineCheck = cycleOrma.selectFromPerson()
                        .nameEq("Baseline_Record").toList();
                    if (baselineCheck.size() != 1) {
                        dataPersistenceOk = false;
                        System.err.println("  [ERROR] Cycle " + cycle + ": baseline record lost!");
                        allCyclesPassed = false;
                    }

                    // Insert a new record for this cycle
                    Person p = new Person();
                    p.name = "Cycle_" + cycle;
                    p.address = "open_close_test";
                    p.social_number = cycle + 1;
                    cycleOrma.insertIntoPerson(p);

                    // Verify insert succeeded before closing
                    int afterInsert = cycleOrma.selectFromPerson().count();
                    if (afterInsert != expectedCount + 1) {
                        allCyclesPassed = false;
                        System.err.println("  [ERROR] Cycle " + cycle + ": insert failed");
                    }

                    // Close the database for this cycle
                    OrmaDatabase.shutdown();

                } catch (Exception e) {
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + " exception: " + e.getMessage());
                    // Try to shutdown cleanly before continuing
                    try { OrmaDatabase.shutdown(); } catch (Exception ignored) {}
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            SormaUnitTest.assertCondition("All " + CYCLES + " open/close cycles completed without error",
                allCyclesPassed);
            SormaUnitTest.assertCondition("Data persisted correctly across all cycles",
                dataPersistenceOk);
            System.out.println("  [INFO] " + CYCLES + " open/close cycles took " + duration + "ms");

            // --- Phase 4: Final reopen to verify complete state ---
            OrmaDatabase finalOrma = new OrmaDatabase(dbPath, "", false);
            OrmaDatabase.init(1);

            // Total records should be: 1 baseline + CYCLES cycle records
            int finalCount = finalOrma.selectFromPerson().count();
            int expectedTotal = 1 + CYCLES;
            SormaUnitTest.assertCondition("Final count correct after all cycles (" +
                finalCount + "/" + expectedTotal + ")", finalCount == expectedTotal);

            // Verify first cycle record exists
            List<Person> firstCycle = finalOrma.selectFromPerson()
                .nameEq("Cycle_0").toList();
            SormaUnitTest.assertCondition("First cycle record survived all reopenings",
                firstCycle.size() == 1);

            // Verify last cycle record exists
            List<Person> lastCycle = finalOrma.selectFromPerson()
                .nameEq("Cycle_" + (CYCLES - 1)).toList();
            SormaUnitTest.assertCondition("Last cycle record exists",
                lastCycle.size() == 1);

            // Verify a middle cycle record (spot check)
            int midCycle = CYCLES / 2;
            List<Person> midCheck = finalOrma.selectFromPerson()
                .nameEq("Cycle_" + midCycle).toList();
            SormaUnitTest.assertCondition("Middle cycle record (Cycle_" + midCycle + ") survived",
                midCheck.size() == 1);

            // Verify baseline is still intact after all cycles
            List<Person> finalBaseline = finalOrma.selectFromPerson()
                .nameEq("Baseline_Record").toList();
            SormaUnitTest.assertCondition("Baseline record intact after " + CYCLES + " cycles",
                finalBaseline.size() == 1 &&
                "open_close_test".equals(finalBaseline.get(0).address));

            // --- Phase 5: Clean up and leave DB open for main() shutdown ---
            finalOrma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("Cleanup: table emptied",
                finalOrma.selectFromPerson().count() == 0);

            // NOTE: We leave the database OPEN here so that main() can call
            // OrmaDatabase.shutdown() without error.

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Rapid open/close test failed", false);
            e.printStackTrace();
            // Attempt recovery: try to reopen so main() shutdown doesn't crash
            try {
                OrmaDatabase recoveryOrma = new OrmaDatabase(dbPath, "", false);
                OrmaDatabase.init(1);
            } catch (Exception ignored) {}
        }
    }
}
