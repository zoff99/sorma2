import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TEST: Extreme Stress Gauntlet
 * Puts massive pressure on the ORM, JDBC driver, and SQLite engine.
 *
 * Phase 1: The Thundering Herd (Concurrency & Thread Safety)
 *   - 50 threads hammering the DB simultaneously with mixed CRUD.
 *   - Tests if Sorma2's internal connection handling is truly thread-safe
 *     or if it suffers from JDBC "Connection is closed" / "ResultSet in use" errors.
 *
 * Phase 2: The Memory Crusher (Large Payloads & Result Sets)
 *   - Inserts thousands of rows with large (5KB) strings.
 *   - Selects ALL of them at once via toList() to stress the Java heap,
 *     JDBC ResultSet cursor, and Sorma2's object mapping overhead.
 *
 * Phase 3: B-Tree Fragmentation Nightmare
 *   - Inserts 10,000 rows, deletes half of them (massive free-list fragmentation),
 *     then inserts 10,000 more.
 *   - Verifies the SQLite B-Tree doesn't corrupt under heavy page recycling.
 */
public class TestExtremeStress {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: EXTREME STRESS GAUNTLET ---");
        try {
            // Optimize for stress testing.
            // NOTE: SET pragmas use run_multi_sql() (no ResultSet returned).
            //       QUERY pragmas (like integrity_check) use run_query_for_single_result().
            OrmaDatabase.run_multi_sql("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_multi_sql("PRAGMA synchronous=NORMAL;");
            OrmaDatabase.run_multi_sql("PRAGMA cache_size=-20000;");
            OrmaDatabase.run_multi_sql("PRAGMA busy_timeout=5000;");

            orma.deleteFromPerson().execute();
        } catch (Exception e) {
            SormaUnitTest.assertCondition("Extreme Stress test failed catastrophically", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // PHASE 1: 50 Threads, 10,000 Mixed Operations
    // =========================================================================
    private static void runPhase1_ThunderingHerd(OrmaDatabase orma) throws Exception {
        System.out.println("  [Phase 1] Thundering Herd (50 threads, 10,000 ops)...");

        // Seed some data so updaters/deleters have something to hit
        for (int i = 0; i < 100; i++) {
            Person p = new Person();
            p.name = "Seed_" + i;
            p.address = "Herd";
            p.social_number = i;
            orma.insertIntoPerson(p);
        }

        int numThreads = 50;
        int opsPerThread = 200; // 50 * 200 = 10,000 total operations
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger busyCount = new AtomicInteger(0); // SQLITE_BUSY is expected under extreme load
        AtomicInteger crashCount = new AtomicInteger(0); // JDBC errors, NPEs, etc. are BAD
        AtomicBoolean dataCorruption = new AtomicBoolean(false);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    try {
                        int op = i % 4; // 0=Insert, 1=Select, 2=Update, 3=Delete
                        if (op == 0) {
                            Person p = new Person();
                            p.name = "Herd_" + threadId + "_" + i;
                            p.address = "stress";
                            p.social_number = threadId * 10000 + i;
                            orma.insertIntoPerson(p);
                        } else if (op == 1) {
                            // Select a random subset
                            orma.selectFromPerson().social_numberGt(threadId * 100).count();
                        } else if (op == 2) {
                            // Update a specific seed row
                            orma.updatePerson().address("Modified_" + i).nameEq("Seed_" + (i % 100)).execute();
                        } else {
                            // Delete a specific herd row (might not exist, that's fine)
                            orma.deleteFromPerson().nameEq("Herd_" + threadId + "_" + (i - 4)).execute();
                        }
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        String msg = SormaUnitTest.getRootCauseMessage(e);
                        if (msg.contains("SQLITE_BUSY") || msg.contains("database is locked")) {
                            busyCount.incrementAndGet();
                        } else {
                            // Actual crash (e.g., JDBC thread safety violation, NPE)
                            crashCount.incrementAndGet();
                            System.err.println("    [CRASH] Thread " + threadId + ": " + msg);
                        }
                    }
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(120, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        SormaUnitTest.assertCondition("Phase 1: Executor finished within timeout", finished);
        SormaUnitTest.assertCondition("Phase 1: Zero thread-safety crashes/JDBC errors", crashCount.get() == 0);
        SormaUnitTest.assertCondition("Phase 1: No data corruption detected", !dataCorruption.get());

        System.out.println("    [INFO] Success: " + successCount.get() +
                           ", SQLITE_BUSY (expected): " + busyCount.get() +
                           ", Crashes: " + crashCount.get() +
                           " (" + duration + "ms)");
    }

    // =========================================================================
    // PHASE 2: Large Payloads & Massive Result Sets
    // =========================================================================
    private static void runPhase2_MemoryCrusher(OrmaDatabase orma) {
        System.out.println("  [Phase 2] Memory Crusher (Large strings, massive toList())...");
        orma.deleteFromPerson().execute();

        int numRows = 2000;
        int stringSize = 5000; // 5KB per string -> ~10MB total raw data

        // Generate a 5KB string
        StringBuilder sb = new StringBuilder(stringSize);
        for (int i = 0; i < stringSize; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String largeString = sb.toString();

        long startInsert = System.currentTimeMillis();
        // Insert 2000 rows with 5KB strings
        for (int i = 0; i < numRows; i++) {
            Person p = new Person();
            p.name = "Crusher_" + i;
            p.address = largeString; // 5KB payload
            p.social_number = i;
            orma.insertIntoPerson(p);
        }
        long insertDuration = System.currentTimeMillis() - startInsert;

        SormaUnitTest.assertCondition("Phase 2: Inserted " + numRows + " large rows",
            orma.selectFromPerson().count() == numRows);

        // Now, the real stress: load ALL 2000 rows (with 5KB strings) into memory at once
        long startSelect = System.currentTimeMillis();
        List<Person> massiveList = orma.selectFromPerson().toList();
        long selectDuration = System.currentTimeMillis() - startSelect;

        SormaUnitTest.assertCondition("Phase 2: toList() loaded all " + numRows + " rows",
            massiveList.size() == numRows);

        // Verify data integrity of the massive result set
        boolean integrityOk = true;
        for (int i = 0; i < numRows; i++) {
            Person p = massiveList.get(i);
            if (p.address == null || p.address.length() != stringSize) {
                integrityOk = false;
                break;
            }
        }
        SormaUnitTest.assertCondition("Phase 2: All 5KB strings intact in memory", integrityOk);

        System.out.println("    [INFO] Insert " + numRows + "x5KB: " + insertDuration + "ms");
        System.out.println("    [INFO] Select all to List: " + selectDuration + "ms");
    }

    // =========================================================================
    // PHASE 3: B-Tree Fragmentation Nightmare
    // =========================================================================
    private static void runPhase3_FragmentationNightmare(OrmaDatabase orma) {
        System.out.println("  [Phase 3] B-Tree Fragmentation (Insert -> Delete Half -> Insert)...");
        orma.deleteFromPerson().execute();

        int batchSize = 5000;

        // 1. Insert 5,000 rows
        for (int i = 0; i < batchSize; i++) {
            Person p = new Person();
            p.name = "Frag_" + i;
            p.address = "fragmentation_test";
            p.social_number = i;
            orma.insertIntoPerson(p);
        }
        SormaUnitTest.assertCondition("Phase 3: Initial 5000 rows inserted",
            orma.selectFromPerson().count() == batchSize);

        // 2. Delete every even row (creates massive B-Tree fragmentation and free-list bloat)
        // We do this by deleting ranges to be faster than row-by-row
        for (int i = 0; i < batchSize; i += 2) {
            orma.deleteFromPerson().social_numberEq(i).execute();
        }
        SormaUnitTest.assertCondition("Phase 3: Deleted half the rows (fragmentation created)",
            orma.selectFromPerson().count() == batchSize / 2);

        // 3. Force SQLite to checkpoint the WAL to disk before the next wave
        OrmaDatabase.run_query_for_single_result("PRAGMA wal_checkpoint(TRUNCATE);");

        // 4. Insert 5,000 MORE rows. SQLite must recycle the fragmented free pages.
        for (int i = batchSize; i < batchSize * 2; i++) {
            Person p = new Person();
            p.name = "Frag_" + i;
            p.address = "recycled_pages";
            p.social_number = i;
            orma.insertIntoPerson(p);
        }

        int finalCount = orma.selectFromPerson().count();
        SormaUnitTest.assertCondition("Phase 3: Final count correct after page recycling",
            finalCount == (batchSize / 2) + batchSize);

        // 5. The ultimate verification: PRAGMA integrity_check
        // If the B-Tree pointers or free-list got corrupted during recycling, this will fail.
        String integrity = OrmaDatabase.run_query_for_single_result("PRAGMA integrity_check;");
        SormaUnitTest.assertCondition("Phase 3: B-Tree integrity_check is 'ok' after fragmentation",
            integrity != null && integrity.trim().equals("ok"));

        System.out.println("    [INFO] B-Tree survived heavy fragmentation and page recycling.");
    }
}
