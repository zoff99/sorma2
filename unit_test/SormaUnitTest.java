import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;
import java.util.List;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

public class SormaUnitTest {
    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" Sorma2 Plain Java Unit Tests");
        System.out.println("========================================\n");

        String dbPath = "./unit_test_db.sqlite";
        new File(dbPath).delete(); // Clean slate

        OrmaDatabase orma = new OrmaDatabase(dbPath, "", false);

        // Define schema upgrade callback to create tables
        OrmaDatabase.set_schema_upgrade_callback(new schema_upgrade_callback() {
            @Override
            public void upgrade(int old_version, int new_version) {
                System.out.println(">> Schema Upgrade: " + old_version + " -> " + new_version);
                if (new_version >= 1) {
                    OrmaDatabase.run_multi_sql(
                        "CREATE TABLE IF NOT EXISTS \"Person\" (\n" +
                        "  \"id\" INTEGER,\n" +
                        "  \"name\" TEXT,\n" +
                        "  \"address\" TEXT,\n" +
                        "  \"social_number\" INTEGER,\n" +
                        "  PRIMARY KEY(\"id\" AUTOINCREMENT)\n" +
                        ");"
                    );
                }
            }
        });

        try {
            OrmaDatabase.init(1);
        } catch (Exception e) {
            System.out.println("Note: Init exception: " + e.getMessage());
        }

        // Run Tests
        testBasicCrud(orma);
        testSqlInjectionSecurity(orma);
        testSpecialCharactersAndEncoding(orma);
        testSqliteBusy(orma);
        testHeavyThreading(orma);

        try { OrmaDatabase.shutdown(); } catch (Exception e) {}
        new File(dbPath).delete();

        System.out.println("\n========================================");
        System.out.println(" TEST SUMMARY");
        System.out.println("========================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ... (Previous test methods remain the same) ...
    static void testBasicCrud(OrmaDatabase orma) {
        System.out.println("--- Test: Basic CRUD Operations ---");
        try {
            Person p = new Person();
            p.name = "John Doe";
            p.address = "123 Main St";
            p.social_number = 12345;
            
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert returns valid row ID", rowId > 0);

            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Query returned exactly 1 result", results.size() == 1);
            
            Person selected = results.get(0);
            assertCondition("Selected name matches", "John Doe".equals(selected.name));
            assertCondition("Selected int matches", selected.social_number == 12345);
            
            orma.deleteFromPerson().idEq(rowId).execute();
            int count = orma.selectFromPerson().count();
            assertCondition("Delete removes record", count == 0);
        } catch (Exception e) {
            assertCondition("CRUD operations threw no exceptions", false);
            e.printStackTrace();
        }
    }

    static void testSqlInjectionSecurity(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQL Injection Security ---");
        try {
            String maliciousPayload = "Robert'); DROP TABLE Person; --";
            
            Person p = new Person();
            p.name = maliciousPayload;
            p.address = "Hell";
            p.social_number = 666;
            
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert malicious payload", rowId > 0);

            int count = orma.selectFromPerson().count();
            assertCondition("Table still exists (Not dropped by injection!)", count >= 1);

            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Payload stored exactly as literal string", maliciousPayload.equals(results.get(0).name));
            
        } catch (Exception e) {
            assertCondition("SQL Injection test failed (Vulnerability detected!)", false);
            e.printStackTrace();
        }
    }

    static void testSpecialCharactersAndEncoding(OrmaDatabase orma) {
        System.out.println("\n--- Test: Special Characters & Encoding ---");
        try {
            Person p = new Person();
            p.name = "Alice 😊🚀"; // Emojis
            p.address = "O'Connor's \"House\" \n \t \\"; // Quotes, newlines, escapes
            p.social_number = 999;
            
            long rowId = orma.insertIntoPerson(p);
            
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            Person selected = results.get(0);
            
            assertCondition("Emojis preserved (UTF-8 Check)", "Alice 😊🚀".equals(selected.name));
            assertCondition("Quotes and escapes preserved", "O'Connor's \"House\" \n \t \\".equals(selected.address));
            
        } catch (Exception e) {
            assertCondition("Special characters test failed", false);
            e.printStackTrace();
        }
    }

    // --- NEW TEST 1: SQLITE_BUSY Handling ---
    static void testSqliteBusy(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQLITE_BUSY Handling ---");
        Connection blockerConn = null;
        try {
            // 1. Set busy timeout to 0 so the ORM fails immediately when locked
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 0;");
            
            // 2. Open a second raw JDBC connection and lock the database
            Class.forName("org.sqlite.JDBC");
            blockerConn = DriverManager.getConnection("jdbc:sqlite:unit_test_db.sqlite");
            blockerConn.setAutoCommit(false);
            
            // Performing a write operation acquires a RESERVED lock, blocking other writers
            Statement stmt = blockerConn.createStatement();
            stmt.executeUpdate("INSERT INTO Person (name, address, social_number) VALUES ('Blocker', 'Holding Lock', 0)");
            
            // 3. Try to insert via ORM. This should fail with SQLITE_BUSY
            Person p = new Person();
            p.name = "ShouldFail";
            p.address = "Busy";
            p.social_number = 999;
            
            boolean busyDetected = false;
            try {
                orma.insertIntoPerson(p);
            } catch (Exception e) {
                String msg = getRootCauseMessage(e);
                if (msg.contains("SQLITE_BUSY") || msg.contains("database is locked")) {
                    busyDetected = true;
                }
            }
            
            assertCondition("SQLITE_BUSY correctly thrown when DB is locked", busyDetected);
            
        } catch (Exception e) {
            e.printStackTrace();
            assertCondition("SQLITE_BUSY test failed unexpectedly", false);
        } finally {
            // 4. Cleanup: Release lock and restore busy timeout
            if (blockerConn != null) {
                try { blockerConn.rollback(); blockerConn.close(); } catch (SQLException e) {}
            }
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");
        }
    }

    // --- NEW TEST 2: Heavy Threading (Concurrency) ---
    static void testHeavyThreading(OrmaDatabase orma) {
        System.out.println("\n--- Test: Heavy Threading (WAL Mode) ---");
        try {
            // Enable WAL mode for better concurrency and set a busy timeout
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
            
            // Wait for all threads to finish
            for (Thread t : readers) t.join();
            for (Thread t : writers) t.join();
            
            int finalCount = orma.selectFromPerson().count();
            int expectedCount = numWriters * iterations;
            
            assertCondition("No exceptions in heavy threading test", !errorOccurred[0]);
            assertCondition("All concurrent inserts succeeded (" + finalCount + "/" + expectedCount + ")", finalCount == expectedCount);
            
        } catch (Exception e) {
            e.printStackTrace();
            assertCondition("Heavy threading test failed", false);
        }
    }

    static String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    static void assertCondition(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            failed++;
        }
    }
}
