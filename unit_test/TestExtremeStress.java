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
 * Phase 2: The Memory Crusher (Large Payloads & Result Sets)
 * Phase 3: B-Tree Fragmentation Nightmare
 */
public class TestExtremeStress {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: EXTREME STRESS GAUNTLET ---");
        try {
            // Set pragmas one at a time with individual error handling.
            // If one fails, we continue with defaults rather than killing the test.
            try {
                OrmaDatabase.run_multi_sql("PRAGMA journal_mode=WAL;");
            } catch (Exception e) {
                System.out.println("  [WARN] journal_mode=WAL failed: " + e.getMessage());
            }
            try {
                OrmaDatabase.run_multi_sql("PRAGMA synchronous=NORMAL;");
            } catch (Exception e) {
                System.out.println("  [WARN] synchronous=NORMAL failed: " + e.getMessage());
            }
            try {
                // Use positive value (number of pages) instead of negative (KiB)
                OrmaDatabase.run_multi_sql("PRAGMA cache_size=10000;");
            } catch (Exception e) {
                System.out.println("  [WARN] cache_size failed: " + e.getMessage());
            }
            try {
                OrmaDatabase.run_multi_sql("PRAGMA busy_timeout=5000;");
            } catch (Exception e) {
                System.out.println("  [WARN] busy_timeout failed: " + e.getMessage());
            }

            // Clean table before starting
            orma.deleteFromPerson().execute();

            System.out.println("  [SETUP] Pragmas configured, starting Phase 1...");
            runPhase1_ThunderingHerd(orma);

            System.out.println("  [SETUP] Phase 1 complete, starting Phase 2...");
            runPhase2_MemoryCrusher(orma);

            System.out.println("  [SETUP] Phase 2 complete, starting Phase 3...");
            runPhase3_FragmentationNightmare(orma);

            System.out.println("  [SETUP] Phase 3 complete. Stress test finished.");

            // Final cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Extreme Stress test failed catastrophically", false);
            System.err.println("  [FATAL] Exception: " + e.getClass().getName() + ": " + e.getMessage());
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
        AtomicInteger busyCount = new AtomicInteger(0);
        AtomicInteger crashCount = new AtomicInteger(0);
        AtomicBoolean dataCorruption = new AtomicBoolean(false);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    try {
                        int op = i % 4;
                        if (op == 0) {
                            Person p = new Person();
                            p.name = "Herd_" + threadId + "_" + i;
                            p.address = "stress";
                            p.social_number = threadId * 10000 + i;
                            orma.insertIntoPerson(p);
                        } else if (op == 1) {
                            orma.selectFromPerson().social_numberGt(threadId * 100).count();
                        } else if (op == 2) {
                            orma.updatePerson().address("Modified_" + i).nameEq("Seed_" + (i % 100)).execute();
                        } else {
                            orma.deleteFromPerson().nameEq("Herd_" + threadId + "_" + (i - 4)).execute();
                        }
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        String msg = SormaUnitTest.getRootCauseMessage(e);
                        if (msg.contains("SQLITE_BUSY") || msg.contains("database is locked")) {
                            busyCount.incrementAndGet();
                        } else {
                            crashCount.incrementAndGet();
                        }
                    }
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(120, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        SormaUnitTest.assertCondition("Phase 1: Executor finished within timeout", finished);
        SormaUnitTest.assertCondition("Phase 1: Zero thread-safety crashes/JDBC errors",
            crashCount.get() == 0);

        System.out.println("    [INFO] Success: " + successCount.get() +
                           ", SQLITE_BUSY: " + busyCount.get() +
                           ", Crashes: " + crashCount.get() +
                           " (" + duration + "ms)");
    }

    // =========================================================================
    // PHASE 2: Large Payloads & Massive Result Sets
    // =========================================================================
    private static void runPhase2_MemoryCrusher(OrmaDatabase orma) {
        System.out.println("  [Phase 2] Memory Crusher (2000 rows x 5KB strings)...");
        orma.deleteFromPerson().execute();

        int numRows = 2000;
        int stringSize = 5000; // 5KB per string

        // Generate a 5KB string
        StringBuilder sb = new StringBuilder(stringSize);
        for (int i = 0; i < stringSize; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String largeString = sb.toString();

        long startInsert = System.currentTimeMillis();
        for (int i = 0; i < numRows; i++) {
            Person p = new Person();
            p.name = "Crusher_" + i;
            p.address = largeString;
            p.social_number = i;
            orma.insertIntoPerson(p);
        }
        long insertDuration = System.currentTimeMillis() - startInsert;

        SormaUnitTest.assertCondition("Phase 2: Inserted " + numRows + " large rows",
            orma.selectFromPerson().count() == numRows);

        // Load ALL rows into memory at once
        long startSelect = System.currentTimeMillis();
        List<Person> massiveList = orma.selectFromPerson().toList();
        long selectDuration = System.currentTimeMillis() - startSelect;

        SormaUnitTest.assertCondition("Phase 2: toList() loaded all " + numRows + " rows",
            massiveList.size() == numRows);

        // Verify data integrity
        boolean integrityOk = true;
        for (int i = 0; i < numRows; i++) {
            Person p = massiveList.get(i);
            if (p.address == null || p.address.length() != stringSize) {
                integrityOk = false;
                break;
            }
        }
        SormaUnitTest.assertCondition("Phase 2: All 5KB strings intact in memory", integrityOk);

        System.out.println("    [INFO] Insert: " + insertDuration + "ms, Select all: " + selectDuration + "ms");
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

        // 2. Delete every even-numbered row (creates fragmentation)
        for (int i = 0; i < batchSize; i += 2) {
            orma.deleteFromPerson().social_numberEq(i).execute();
        }
        SormaUnitTest.assertCondition("Phase 3: Deleted half (fragmentation created)",
            orma.selectFromPerson().count() == batchSize / 2);

        // 3. Checkpoint WAL to disk
        try {
            OrmaDatabase.run_query_for_single_result("PRAGMA wal_checkpoint(TRUNCATE);");
        } catch (Exception e) {
            // Non-fatal if checkpoint fails
        }

        // 4. Insert 5,000 MORE rows into fragmented space
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

        // 5. Verify B-Tree integrity
        String integrity = OrmaDatabase.run_query_for_single_result("PRAGMA integrity_check;");
        SormaUnitTest.assertCondition("Phase 3: B-Tree integrity_check is 'ok'",
            integrity != null && integrity.trim().equals("ok"));

        System.out.println("    [INFO] B-Tree survived fragmentation and page recycling.");
    }
}
