import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TEST: Concurrent DELETE While Reading
 * One thread continuously reads from the table while another thread
 * deletes records. Verifies that:
 *   - Reader never sees corrupted/partial data
 *   - Reader never crashes due to concurrent deletion
 *   - Reader always sees a consistent snapshot (valid records only)
 *   - No SQLITE_BUSY errors leak through unhandled
 */
public class TestConcurrentDeleteWhileReading {

    static final int INITIAL_RECORDS = 200;
    static final int DELETE_BATCH = 10;
    static final int READ_ITERATIONS = 100;

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Concurrent DELETE While Reading ---");
        try {
            // Enable WAL and set busy timeout
            OrmaDatabase.run_query_for_single_result("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");

            // Clean up and insert initial records
            orma.deleteFromPerson().execute();
            for (int i = 0; i < INITIAL_RECORDS; i++) {
                Person p = new Person();
                p.name = "ConcurrentDel_" + i;
                p.address = "reader_test";
                p.social_number = i;
                orma.insertIntoPerson(p);
            }
            SormaUnitTest.assertCondition("Setup: " + INITIAL_RECORDS + " records inserted",
                orma.selectFromPerson().count() == INITIAL_RECORDS);

            // Shared state
            AtomicBoolean corruptionDetected = new AtomicBoolean(false);
            AtomicBoolean readerCrashed = new AtomicBoolean(false);
            AtomicBoolean deleterCrashed = new AtomicBoolean(false);
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicBoolean deleteDone = new AtomicBoolean(false);

            // --- Reader thread: continuously reads and validates data ---
            Thread reader = new Thread(() -> {
                for (int i = 0; i < READ_ITERATIONS && !deleteDone.get(); i++) {
                    try {
                        // Read all records
                        List<Person> records = orma.selectFromPerson().toList();

                        // Validate each record is not corrupted
                        for (Person record : records) {
                            // Every record should have a valid name starting with our prefix
                            if (record.name != null && !record.name.startsWith("ConcurrentDel_")) {
                                corruptionDetected.set(true);
                            }
                            // social_number should be non-negative
                            if (record.social_number < 0) {
                                corruptionDetected.set(true);
                            }
                        }

                        successfulReads.incrementAndGet();

                        // Small delay to interleave with deletes
                        Thread.sleep(1);
                    } catch (Exception e) {
                        String msg = SormaUnitTest.getRootCauseMessage(e);
                        // SQLITE_BUSY is acceptable under concurrent access
                        if (!msg.contains("SQLITE_BUSY") && !msg.contains("database is locked")) {
                            readerCrashed.set(true);
                            System.err.println("  [ERROR] Reader crashed: " + msg);
                        }
                    }
                }
            });

            // --- Deleter thread: deletes records in batches ---
            Thread deleter = new Thread(() -> {
                try {
                    for (int batch = 0; batch < INITIAL_RECORDS / DELETE_BATCH; batch++) {
                        int start = batch * DELETE_BATCH;
                        int end = start + DELETE_BATCH;

                        // Delete records by social_number range
                        orma.deleteFromPerson()
                            .social_numberGe(start)
                            .social_numberLt(end)
                            .execute();

                        // Small delay between batches
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    String msg = SormaUnitTest.getRootCauseMessage(e);
                    if (!msg.contains("SQLITE_BUSY") && !msg.contains("database is locked")) {
                        deleterCrashed.set(true);
                        System.err.println("  [ERROR] Deleter crashed: " + msg);
                    }
                } finally {
                    deleteDone.set(true);
                }
            });

            // Start both threads
            reader.start();
            deleter.start();

            // Wait for completion
            reader.join();
            deleter.join();

            // --- Verify results ---
            SormaUnitTest.assertCondition("No corruption detected during concurrent read/delete",
                !corruptionDetected.get());
            SormaUnitTest.assertCondition("Reader did not crash", !readerCrashed.get());
            SormaUnitTest.assertCondition("Deleter did not crash", !deleterCrashed.get());
            SormaUnitTest.assertCondition("Reader completed multiple reads (" +
                successfulReads.get() + ")", successfulReads.get() > 0);

            // After all deletes, table should be empty
            int finalCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("All records deleted successfully", finalCount == 0);

            System.out.println("  [INFO] Reader completed " + successfulReads.get() +
                " reads during deletion of " + INITIAL_RECORDS + " records");

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Concurrent delete-while-reading test failed", false);
            e.printStackTrace();
        }
    }
}
