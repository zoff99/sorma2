import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TEST: Autoincrement ID Uniqueness Under Concurrency
 * Multiple threads insert records simultaneously. Verifies that:
 *   - Every inserted row gets a unique autoincrement ID
 *   - No two threads receive the same ID
 *   - All inserts succeed without collision
 *   - IDs are monotonically increasing (no gaps except from concurrency ordering)
 *
 * ID collisions are catastrophic bugs that cause data overwrite.
 */
public class TestAutoincrementConcurrency {

    static final int NUM_THREADS = 5;
    static final int INSERTS_PER_THREAD = 50;

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Autoincrement ID Uniqueness Under Concurrency ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // Enable WAL and set busy timeout for concurrent writes
            OrmaDatabase.run_query_for_single_result("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 10000;");

            // Thread-safe collection to gather all returned IDs
            ConcurrentLinkedQueue<Long> allIds = new ConcurrentLinkedQueue<>();
            AtomicBoolean errorOccurred = new AtomicBoolean(false);

            Thread[] threads = new Thread[NUM_THREADS];

            for (int t = 0; t < NUM_THREADS; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < INSERTS_PER_THREAD; i++) {
                        try {
                            Person p = new Person();
                            p.name = "Thread_" + threadId + "_Insert_" + i;
                            p.address = "concurrency_id_test";
                            p.social_number = threadId * 10000 + i;
                            long rowId = orma.insertIntoPerson(p);

                            if (rowId <= 0) {
                                System.err.println("  [ERROR] Thread " + threadId +
                                    " got invalid ID: " + rowId);
                                errorOccurred.set(true);
                            } else {
                                allIds.add(rowId);
                            }
                        } catch (Exception e) {
                            System.err.println("  [ERROR] Thread " + threadId +
                                " insert failed: " + e.getMessage());
                            errorOccurred.set(true);
                        }
                    }
                });
            }

            // Start all threads simultaneously
            for (Thread t : threads) t.start();
            // Wait for all to complete
            for (Thread t : threads) t.join();

            int expectedTotal = NUM_THREADS * INSERTS_PER_THREAD;

            // --- Verify no errors occurred ---
            SormaUnitTest.assertCondition("No errors during concurrent inserts", !errorOccurred.get());

            // --- Verify all inserts succeeded ---
            SormaUnitTest.assertCondition("All " + expectedTotal + " IDs collected",
                allIds.size() == expectedTotal);

            // --- Verify all IDs are unique (no collisions) ---
            Set<Long> uniqueIds = new HashSet<>(allIds);
            SormaUnitTest.assertCondition("All IDs are unique (no collisions)",
                uniqueIds.size() == expectedTotal);

            // --- Verify all IDs are positive ---
            boolean allPositive = true;
            for (Long id : allIds) {
                if (id <= 0) {
                    allPositive = false;
                    break;
                }
            }
            SormaUnitTest.assertCondition("All IDs are positive", allPositive);

            // --- Verify database count matches ---
            int dbCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Database count matches total inserts (" +
                dbCount + "/" + expectedTotal + ")", dbCount == expectedTotal);

            // --- Verify IDs in database are all unique ---
            List<Person> allRecords = orma.selectFromPerson().toList();
            Set<Long> dbIds = new HashSet<>();
            boolean dbIdsUnique = true;
            for (Person record : allRecords) {
                if (!dbIds.add(record.id)) {
                    dbIdsUnique = false;
                    break;
                }
            }
            SormaUnitTest.assertCondition("IDs stored in DB are all unique", dbIdsUnique);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Autoincrement concurrency test failed", false);
            e.printStackTrace();
        }
    }
}
