import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Rapid Insert → Update → Delete Lifecycle
 * Performs a complete CRUD lifecycle on a single row repeatedly in a tight
 * loop. This catches state leaks, stale references, or corruption that
 * might only appear after many repeated operations on the same table.
 *
 * Each cycle: INSERT → verify → UPDATE → verify → DELETE → verify empty
 */
public class TestRapidLifecycle {

    // Number of full lifecycle cycles to perform
    static final int CYCLES = 100;

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Rapid Insert→Update→Delete Lifecycle (" + CYCLES + "x) ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            boolean allCyclesPassed = true;
            long startTime = System.currentTimeMillis();

            for (int cycle = 0; cycle < CYCLES; cycle++) {
                // --- INSERT ---
                Person p = new Person();
                p.name = "Lifecycle_" + cycle;
                p.address = "iteration_" + cycle;
                p.social_number = cycle;
                long rowId = orma.insertIntoPerson(p);

                if (rowId <= 0) {
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + ": insert failed");
                    continue;
                }

                // --- VERIFY INSERT ---
                List<Person> inserted = orma.selectFromPerson().idEq(rowId).toList();
                if (inserted.size() != 1 || !("Lifecycle_" + cycle).equals(inserted.get(0).name)) {
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + ": insert verification failed");
                    continue;
                }

                // --- UPDATE ---
                orma.updatePerson()
                    .name("Updated_" + cycle)
                    .address("modified_" + cycle)
                    .social_number(cycle + 1000)
                    .idEq(rowId)
                    .execute();

                // --- VERIFY UPDATE ---
                List<Person> updated = orma.selectFromPerson().idEq(rowId).toList();
                if (updated.size() != 1 ||
                    !("Updated_" + cycle).equals(updated.get(0).name) ||
                    updated.get(0).social_number != (cycle + 1000)) {
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + ": update verification failed");
                    continue;
                }

                // --- DELETE ---
                orma.deleteFromPerson().idEq(rowId).execute();

                // --- VERIFY DELETE ---
                int countAfterDelete = orma.selectFromPerson().count();
                if (countAfterDelete != 0) {
                    allCyclesPassed = false;
                    System.err.println("  [ERROR] Cycle " + cycle + ": delete failed, count=" + countAfterDelete);
                    // Clean up for next cycle
                    orma.deleteFromPerson().execute();
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            SormaUnitTest.assertCondition("All " + CYCLES + " lifecycle cycles passed", allCyclesPassed);
            SormaUnitTest.assertCondition("Table is empty after all cycles",
                orma.selectFromPerson().count() == 0);
            System.out.println("  [INFO] " + CYCLES + " lifecycles (insert+update+delete) took " + duration + "ms");
            // Performance sanity: each cycle has 6 DB operations, should be fast
            SormaUnitTest.assertCondition("Lifecycle performance acceptable (< 30s)", duration < 30000);

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Rapid lifecycle test failed", false);
            e.printStackTrace();
        }
    }
}

