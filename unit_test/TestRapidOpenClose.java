import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.io.PrintStream;
import java.io.OutputStream;

/**
 * TEST: Rapid Open/Close Cycles
 * Repeatedly opens the database, verifies previously stored data persists,
 * inserts a new record, and closes the database.
 *
 * NOTE: Sorma2's internal logger prints verbose output on every init/shutdown.
 *       We suppress it during the loop by temporarily redirecting System.out.
 *
 * NOTE: This test disrupts the shared database connection, so it MUST
 *       be called LAST in the test suite.
 */
public class TestRapidOpenClose {

    // Number of open/close cycles to perform
    static final int CYCLES = 50;

    // A PrintStream that discards all output (used to suppress Sorma2 logs)
    private static final PrintStream NULL_STREAM = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {}
        @Override
        public void write(byte[] b, int off, int len) {}
    });

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Rapid Open/Close Cycles (" + CYCLES + " cycles) ---");

        String dbPath = "./unit_test_db.sqlite";

        // Save the original System.out so we can restore it later
        PrintStream originalOut = System.out;

        try {
            // --- Phase 1: Clean the table and insert a baseline record ---
            orma.deleteFromPerson().execute();

            Person baseline = new Person();
            baseline.name = "Baseline_Record";
            baseline.address = "open_close_test";
            baseline.social_number = 0;
            orma.insertIntoPerson(baseline);

            SormaUnitTest.assertCondition("Setup: baseline record inserted",
                orma.selectFromPerson().count() == 1);

            // --- Phase 2: Shutdown the current connection ---
            // Suppress Sorma2 log output during shutdown
            System.setOut(NULL_STREAM);
            OrmaDatabase.shutdown();
            System.setOut(originalOut);

            // --- Phase 3: Rapid open/close cycles ---
            boolean allCyclesPassed = true;
            boolean dataPersistenceOk = true;
            long startTime = System.currentTimeMillis();

            for (int cycle = 0; cycle < CYCLES; cycle++) {
                try {
                    // Suppress Sorma2's verbose init/shutdown logs
                    System.setOut(NULL_STREAM);

                    // Reopen the database
                    OrmaDatabase cycleOrma = new OrmaDatabase(dbPath, "", false);
                    OrmaDatabase.init(1);

                    // Restore output for our own error reporting
                    System.setOut(originalOut);

                    // Verify ALL previously inserted records still exist
                    int expectedCount = 1 + cycle; // 1 baseline + N cycle records
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

                    // Verify insert succeeded
                    int afterInsert = cycleOrma.selectFromPerson().count();
                    if (afterInsert != expectedCount + 1) {
                        allCyclesPassed = false;
                        System.err.println("  [ERROR] Cycle " + cycle + ": insert failed");
                    }

                    // Suppress logs during shutdown
                    System.setOut(NULL_STREAM);
                    OrmaDatabase.shutdown();
                    System.setOut(originalOut);

                } catch (Exception e) {
                    System.setOut(originalOut); // Ensure output is restored on error
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + " exception: " + e.getMessage());
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
            // Suppress init logs
            System.setOut(NULL_STREAM);
            OrmaDatabase finalOrma = new OrmaDatabase(dbPath, "", false);
            OrmaDatabase.init(1);
            System.setOut(originalOut);

            // Total records: 1 baseline + CYCLES cycle records
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

            // Leave database OPEN so main() can call OrmaDatabase.shutdown()

        } catch (Exception e) {
            System.setOut(originalOut); // Always restore output on error
            SormaUnitTest.assertCondition("Rapid open/close test failed", false);
            e.printStackTrace();
            // Attempt recovery so main() shutdown doesn't crash
            try {
                OrmaDatabase recoveryOrma = new OrmaDatabase(dbPath, "", false);
                OrmaDatabase.init(1);
            } catch (Exception ignored) {}
        }
    }
}

