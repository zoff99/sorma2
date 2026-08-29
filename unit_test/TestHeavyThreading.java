import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;

/**
 * TEST: Heavy Threading (WAL Mode)
 * Enables WAL mode and spawns multiple concurrent reader and writer threads
 * to verify thread safety and data integrity under concurrent access.
 */
public class TestHeavyThreading {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Heavy Threading (WAL Mode) ---");
        try {
            // Enable WAL mode for better concurrency and set busy timeout
            OrmaDatabase.run_query_for_single_result("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");

            // Clear table for this test
            orma.deleteFromPerson().execute();

            int numWriters = 5;
            int numReaders = 10;
            int iterations = 20;
            final boolean[] errorOccurred = {false};

            Thread[] writers = new Thread[numWriters];
            Thread[] readers = new Thread[numReaders];

            // Create writer threads
            for (int i = 0; i < numWriters; i++) {
                final int tid = i;
                writers[i] = new Thread(() -> {
                    for (int j = 0; j < iterations; j++) {
                        try {
                            Person p = new Person();
                            p.name = "Writer_" + tid + "_" + j;
                            p.address = "Concurrency Test";
                            p.social_number = tid * 1000 + j;
                            orma.insertIntoPerson(p);
                        } catch (Exception e) {
                            System.err.println("Writer thread error: " + e.getMessage());
                            errorOccurred[0] = true;
                        }
                    }
                });
            }

            // Create reader threads
            for (int i = 0; i < numReaders; i++) {
                readers[i] = new Thread(() -> {
                    for (int j = 0; j < iterations; j++) {
                        try {
                            orma.selectFromPerson().count();
                        } catch (Exception e) {
                            System.err.println("Reader thread error: " + e.getMessage());
                            errorOccurred[0] = true;
                        }
                    }
                });
            }

            // Start all threads
            for (Thread t : readers) t.start();
            for (Thread t : writers) t.start();

            // Wait for all threads to complete
            for (Thread t : readers) t.join();
            for (Thread t : writers) t.join();

            // Verify all inserts succeeded
            int finalCount = orma.selectFromPerson().count();
            int expectedCount = numWriters * iterations;

            SormaUnitTest.assertCondition("No exceptions in heavy threading test", !errorOccurred[0]);
            SormaUnitTest.assertCondition("All concurrent inserts succeeded (" + finalCount + "/" + expectedCount + ")",
                finalCount == expectedCount);

        } catch (Exception e) {
            e.printStackTrace();
            SormaUnitTest.assertCondition("Heavy threading test failed", false);
        }
    }
}
