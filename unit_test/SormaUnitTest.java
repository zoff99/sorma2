import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;
import java.util.List;
import java.util.Random;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Sorma2 Plain Java Unit Tests
 * 
 * A dependency-free test harness for the Sorma2 ORM generator.
 * No external testing libraries (JUnit, TestNG, etc.) are required.
 * 
 * Tests cover:
 *   - Basic CRUD operations
 *   - SQL Injection security
 *   - Special characters and encoding
 *   - SQLITE_BUSY handling
 *   - Heavy threading (WAL mode)
 *   - NULL and empty string handling
 *   - UPDATE operations
 *   - Query operators (Lt, Gt, Like, Between, NotEq, etc.)
 *   - ORDER BY sorting
 *   - Bulk insert performance
 *   - Boundary values (integer limits, long strings)
 */
public class SormaUnitTest {

    // Counters for test results
    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" Sorma2 Plain Java Unit Tests");
        System.out.println("========================================\n");

        String dbPath = "./unit_test_db.sqlite";
        new File(dbPath).delete();

        OrmaDatabase orma = new OrmaDatabase(dbPath, "", false);

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

        // =============================================
        // Original tests
        // =============================================
        testBasicCrud(orma);
        testSqlInjectionSecurity(orma);
        testSpecialCharactersAndEncoding(orma);
        testSqliteBusy(orma);
        testHeavyThreading(orma);

        // =============================================
        // Extended tests (batch 1)
        // =============================================
        testNullAndEmptyStrings(orma);
        testUpdateOperations(orma);
        testQueryOperators(orma);
        testOrderBy(orma);
        testBulkInsert(orma);
        testBoundaryValues(orma);

        // =============================================
        // Extended tests (batch 2): binary, random, stress
        // =============================================
        testBinaryDataAsBase64(orma);
        testRandomUnicodeAndControlChars(orma);
        testRapidInsertDeleteCycles(orma);
        testChainedQueryConditions(orma);
        testConcurrentReadWriteIntegrity(orma);

        // Shutdown and cleanup
        try { OrmaDatabase.shutdown(); } catch (Exception e) {}
        new File(dbPath).delete();

        // Print final summary
        System.out.println("\n========================================");
        System.out.println(" TEST SUMMARY");
        System.out.println("========================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        
        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // TEST 1: Basic CRUD Operations
    // Verifies that INSERT, SELECT, and DELETE work correctly.
    // =========================================================================
    static void testBasicCrud(OrmaDatabase orma) {
        System.out.println("--- Test: Basic CRUD Operations ---");
        try {
            // Create a new Person object and set fields
            Person p = new Person();
            p.name = "John Doe";
            p.address = "123 Main St";
            p.social_number = 12345;
            
            // Insert into database
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert returns valid row ID", rowId > 0);

            // Select the inserted row by ID
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Query returned exactly 1 result", results.size() == 1);
            
            // Verify all fields match
            Person selected = results.get(0);
            assertCondition("Selected name matches", "John Doe".equals(selected.name));
            assertCondition("Selected int matches", selected.social_number == 12345);
            
            // Delete the row and verify it's gone
            orma.deleteFromPerson().idEq(rowId).execute();
            int count = orma.selectFromPerson().count();
            assertCondition("Delete removes record", count == 0);
        } catch (Exception e) {
            assertCondition("CRUD operations threw no exceptions", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 2: SQL Injection Security
    // Verifies that malicious SQL payloads are safely stored as literal strings
    // and NOT executed. This confirms the ORM uses PreparedStatement parameters.
    // =========================================================================
    static void testSqlInjectionSecurity(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQL Injection Security ---");
        try {
            // Classic SQL injection payload that attempts to drop the table
            String maliciousPayload = "Robert'); DROP TABLE Person; --";
            
            Person p = new Person();
            p.name = maliciousPayload;
            p.address = "Hell";
            p.social_number = 666;
            
            // Insert the malicious payload
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert malicious payload", rowId > 0);

            // If vulnerable, the table would be dropped and this throws an exception
            int count = orma.selectFromPerson().count();
            assertCondition("Table still exists (Not dropped by injection!)", count >= 1);

            // Verify the payload was stored as a harmless literal string
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Payload stored exactly as literal string", maliciousPayload.equals(results.get(0).name));
            
        } catch (Exception e) {
            assertCondition("SQL Injection test failed (Vulnerability detected!)", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 3: Special Characters & Encoding
    // Verifies that Unicode characters (emojis), quotes, newlines, and escape
    // characters are correctly stored and retrieved without corruption.
    // =========================================================================
    static void testSpecialCharactersAndEncoding(OrmaDatabase orma) {
        System.out.println("\n--- Test: Special Characters & Encoding ---");
        try {
            Person p = new Person();
            p.name = "Alice 😊🚀"; // Emojis (UTF-8 check)
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

    // =========================================================================
    // TEST 4: SQLITE_BUSY Handling
    // Opens a second raw JDBC connection to lock the database, then verifies
    // that the ORM correctly throws a SQLITE_BUSY error when trying to write.
    // This ensures the ORM doesn't silently fail or hang on lock contention.
    // =========================================================================
    static void testSqliteBusy(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQLITE_BUSY Handling ---");
        Connection blockerConn = null;
        try {
            // Set busy timeout to 0 so the ORM fails immediately when locked
            // (without waiting/retrying)
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 0;");
            
            // Open a second raw JDBC connection to the same database file.
            // This bypasses the ORM's connection, allowing us to simulate
            // another process holding a lock.
            Class.forName("org.sqlite.JDBC");
            blockerConn = DriverManager.getConnection("jdbc:sqlite:unit_test_db.sqlite");
            blockerConn.setAutoCommit(false);
            
            // Performing a write operation acquires a RESERVED lock,
            // which blocks other writers but allows readers.
            Statement stmt = blockerConn.createStatement();
            stmt.executeUpdate("INSERT INTO Person (name, address, social_number) VALUES ('Blocker', 'Holding Lock', 0)");
            
            // Now try to insert via the ORM. Since the database is locked by
            // the second connection and busy_timeout is 0, this should fail
            // with SQLITE_BUSY.
            Person p = new Person();
            p.name = "ShouldFail";
            p.address = "Busy";
            p.social_number = 999;
            
            boolean busyDetected = false;
            try {
                orma.insertIntoPerson(p);
            } catch (Exception e) {
                // Check if the exception (or its root cause) contains SQLITE_BUSY
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
            // Cleanup: Release the lock by rolling back the second connection
            if (blockerConn != null) {
                try { blockerConn.rollback(); blockerConn.close(); } catch (SQLException e) {}
            }
            // Restore busy timeout for subsequent tests (5 second wait)
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");
        }
    }

    // =========================================================================
    // TEST 5: Heavy Threading (WAL Mode)
    // Enables WAL (Write-Ahead Logging) mode and spawns multiple concurrent
    // reader and writer threads to verify thread safety and data integrity
    // under concurrent access.
    // =========================================================================
    static void testHeavyThreading(OrmaDatabase orma) {
        System.out.println("\n--- Test: Heavy Threading (WAL Mode) ---");
        try {
            // Enable WAL mode for better concurrency (allows concurrent reads
            // while a write is in progress) and set a busy timeout so threads
            // wait for locks instead of failing immediately.
            OrmaDatabase.run_query_for_single_result("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");
            
            // Clear table for this test to get a clean count
            orma.deleteFromPerson().execute();
            
            // Configuration: 5 writer threads, 10 reader threads, 20 iterations each
            int numWriters = 5;
            int numReaders = 10;
            int iterations = 20;
            final boolean[] errorOccurred = {false}; // Shared flag for thread errors
            
            Thread[] writers = new Thread[numWriters];
            Thread[] readers = new Thread[numReaders];
            
            // Create writer threads: each inserts 'iterations' records
            for (int i = 0; i < numWriters; i++) {
                final int tid = i; // Thread ID for unique naming
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
            
            // Create reader threads: each performs COUNT queries
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
            
            // Verify: all inserts should have succeeded
            int finalCount = orma.selectFromPerson().count();
            int expectedCount = numWriters * iterations;
            
            assertCondition("No exceptions in heavy threading test", !errorOccurred[0]);
            assertCondition("All concurrent inserts succeeded (" + finalCount + "/" + expectedCount + ")", finalCount == expectedCount);
            
        } catch (Exception e) {
            e.printStackTrace();
            assertCondition("Heavy threading test failed", false);
        }
    }

    // =========================================================================
    // TEST 6: NULL and Empty String Handling
    // Verifies that NULL values and empty strings are correctly stored and
    // retrieved. This is a common source of ORM bugs where NULL becomes ""
    // or vice versa.
    // =========================================================================
    static void testNullAndEmptyStrings(OrmaDatabase orma) {
        System.out.println("\n--- Test: NULL & Empty String Handling ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Test 6a: Insert with NULL values ---
            // SQLite TEXT columns can store NULL. We verify the ORM handles it.
            Person pNull = new Person();
            pNull.name = null;        // Explicitly NULL
            pNull.address = null;     // Explicitly NULL
            pNull.social_number = 0;  // int cannot be null, defaults to 0
            long rowIdNull = orma.insertIntoPerson(pNull);
            assertCondition("Insert with NULL values succeeds", rowIdNull > 0);

            // Read back the NULL row
            List<Person> nullResults = orma.selectFromPerson().idEq(rowIdNull).toList();
            assertCondition("NULL row retrieved", nullResults.size() == 1);
            assertCondition("NULL name preserved as null", nullResults.get(0).name == null);
            assertCondition("NULL address preserved as null", nullResults.get(0).address == null);

            // --- Test 6b: Insert with empty strings ---
            // Empty string "" is different from NULL in SQL
            Person pEmpty = new Person();
            pEmpty.name = "";         // Empty string, not NULL
            pEmpty.address = "";      // Empty string, not NULL
            pEmpty.social_number = 1;
            long rowIdEmpty = orma.insertIntoPerson(pEmpty);
            assertCondition("Insert with empty strings succeeds", rowIdEmpty > 0);

            // Read back the empty string row
            List<Person> emptyResults = orma.selectFromPerson().idEq(rowIdEmpty).toList();
            assertCondition("Empty string row retrieved", emptyResults.size() == 1);
            assertCondition("Empty string preserved (not NULL)", "".equals(emptyResults.get(0).name));

            // --- Test 6c: IS NULL query ---
            // Verify that IS NULL correctly finds only NULL rows, not empty strings
            List<Person> isNullResults = orma.selectFromPerson().nameIsNull().toList();
            assertCondition("IS NULL finds only NULL rows", isNullResults.size() == 1);

            // --- Test 6d: IS NOT NULL query ---
            // Verify that IS NOT NULL excludes NULL rows
            List<Person> isNotNullResults = orma.selectFromPerson().nameIsNotNull().toList();
            assertCondition("IS NOT NULL excludes NULL rows", isNotNullResults.size() == 1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("NULL/Empty string test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 7: UPDATE Operations
    // Verifies that the generated UPDATE query builder works correctly.
    // Tests: single field update, multi-field update, conditional update,
    // and updating a non-existent row.
    // =========================================================================
    static void testUpdateOperations(OrmaDatabase orma) {
        System.out.println("\n--- Test: UPDATE Operations ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // Insert a test record
            Person p = new Person();
            p.name = "Original Name";
            p.address = "Original Address";
            p.social_number = 100;
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Setup: Insert test record", rowId > 0);

            // --- Test 7a: Update a single field ---
            // The generated API uses: updatePerson().<set_method>().<where_method>().execute()
            orma.updatePerson().name("Updated Name").idEq(rowId).execute();
            List<Person> afterUpdate = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Single field update works", "Updated Name".equals(afterUpdate.get(0).name));
            // Verify other fields are unchanged
            assertCondition("Unmodified field preserved after update", "Original Address".equals(afterUpdate.get(0).address));

            // --- Test 7b: Update multiple fields at once ---
            // After this, row1 has: name="Multi Update", address="New Address", social_number=200
            orma.updatePerson().name("Multi Update").address("New Address").social_number(200).idEq(rowId).execute();
            afterUpdate = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Multi-field update: name", "Multi Update".equals(afterUpdate.get(0).name));
            assertCondition("Multi-field update: address", "New Address".equals(afterUpdate.get(0).address));
            assertCondition("Multi-field update: social_number", afterUpdate.get(0).social_number == 200);

            // --- Test 7c: Conditional update (WHERE clause) ---
            // Insert a second record with social_number = 300
            Person p2 = new Person();
            p2.name = "Conditional Target";
            p2.address = "Target Addr";
            p2.social_number = 300;
            long rowId2 = orma.insertIntoPerson(p2);

            // Update only records where social_number > 250
            // This should ONLY match row2 (300 > 250), NOT row1 (200 < 250)
            orma.updatePerson().address("Conditionally Updated").social_numberGt(250).execute();
            List<Person> row1After = orma.selectFromPerson().idEq(rowId).toList();
            List<Person> row2After = orma.selectFromPerson().idEq(rowId2).toList();
            assertCondition("Conditional update affected target row", "Conditionally Updated".equals(row2After.get(0).address));
            assertCondition("Conditional update did NOT affect non-matching row", "New Address".equals(row1After.get(0).address));

            // --- Test 7d: Update non-existent row (should not crash) ---
            orma.updatePerson().name("Ghost").idEq(99999).execute();
            int countAfterGhost = orma.selectFromPerson().count();
            assertCondition("Update non-existent row doesn't crash or add rows", countAfterGhost == 2);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("UPDATE operations test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 8: Query Operators
    // Tests all generated query builder comparison methods:
    //   Eq, NotEq, Lt, Le, Gt, Ge, Like, NotLike, Between
    // This verifies the generated SQL for each operator is correct.
    //
    // NOTE: Sorma2's Between() uses STRICT comparison (> and <), not >= and <=.
    //       So Between(20, 40) means: x > 20 AND x < 40
    // =========================================================================
    static void testQueryOperators(OrmaDatabase orma) {
        System.out.println("\n--- Test: Query Operators ---");
        try {
            // Clean up and insert test data
            orma.deleteFromPerson().execute();

            // Insert 5 records with known values for operator testing
            // social_number values will be: 10, 20, 30, 40, 50
            for (int i = 1; i <= 5; i++) {
                Person p = new Person();
                p.name = "Person_" + i;
                p.address = "Address_" + i;
                p.social_number = i * 10; // 10, 20, 30, 40, 50
                orma.insertIntoPerson(p);
            }
            assertCondition("Setup: Inserted 5 test records", orma.selectFromPerson().count() == 5);

            // --- Test 8a: Eq (equals) ---
            List<Person> eqResult = orma.selectFromPerson().social_numberEq(30).toList();
            assertCondition("Eq operator: finds exact match", eqResult.size() == 1 && "Person_3".equals(eqResult.get(0).name));

            // --- Test 8b: NotEq (not equals) ---
            List<Person> notEqResult = orma.selectFromPerson().social_numberNotEq(30).toList();
            assertCondition("NotEq operator: excludes match", notEqResult.size() == 4);

            // --- Test 8c: Lt (less than) ---
            // social_number < 30 should match 10, 20
            List<Person> ltResult = orma.selectFromPerson().social_numberLt(30).toList();
            assertCondition("Lt operator: social_number < 30", ltResult.size() == 2);

            // --- Test 8d: Le (less than or equal) ---
            // social_number <= 30 should match 10, 20, 30
            List<Person> leResult = orma.selectFromPerson().social_numberLe(30).toList();
            assertCondition("Le operator: social_number <= 30", leResult.size() == 3);

            // --- Test 8e: Gt (greater than) ---
            // social_number > 30 should match 40, 50
            List<Person> gtResult = orma.selectFromPerson().social_numberGt(30).toList();
            assertCondition("Gt operator: social_number > 30", gtResult.size() == 2);

            // --- Test 8f: Ge (greater than or equal) ---
            // social_number >= 30 should match 30, 40, 50
            List<Person> geResult = orma.selectFromPerson().social_numberGe(30).toList();
            assertCondition("Ge operator: social_number >= 30", geResult.size() == 3);

            // --- Test 8g: Between (STRICT: x > val1 AND x < val2) ---
            // IMPORTANT: Sorma2 Between uses strict > and <, NOT >= and <=
            // Between(15, 45) means: x > 15 AND x < 45 → matches 20, 30, 40
            List<Person> betweenResult = orma.selectFromPerson().social_numberBetween(15, 45).toList();
            assertCondition("Between operator: 15 < x < 45 matches 20,30,40", betweenResult.size() == 3);

            // Between(20, 40) means: x > 20 AND x < 40 → matches only 30
            List<Person> betweenStrict = orma.selectFromPerson().social_numberBetween(20, 40).toList();
            assertCondition("Between operator: strict bounds (only 30)", betweenStrict.size() == 1 && betweenStrict.get(0).social_number == 30);

            // --- Test 8h: Like (pattern matching) ---
            // name LIKE 'Person_%' should match all 5
            List<Person> likeResult = orma.selectFromPerson().nameLike("Person_%").toList();
            assertCondition("Like operator: matches pattern", likeResult.size() == 5);

            // name LIKE '%_3' should match only Person_3
            List<Person> likeSpecific = orma.selectFromPerson().nameLike("%_3").toList();
            assertCondition("Like operator: matches specific pattern", likeSpecific.size() == 1 && "Person_3".equals(likeSpecific.get(0).name));

            // --- Test 8i: NotLike (negative pattern matching) ---
            // name NOT LIKE '%_3' should match 4 records
            List<Person> notLikeResult = orma.selectFromPerson().nameNotLike("%_3").toList();
            assertCondition("NotLike operator: excludes pattern", notLikeResult.size() == 4);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Query operators test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 9: ORDER BY
    // Verifies that the generated ORDER BY methods correctly sort results
    // in ascending and descending order.
    // =========================================================================
    static void testOrderBy(OrmaDatabase orma) {
        System.out.println("\n--- Test: ORDER BY ---");
        try {
            // Clean up and insert unsorted test data
            orma.deleteFromPerson().execute();

            // Insert records in non-sorted order
            String[] names = {"Charlie", "Alice", "Bob", "Diana", "Eve"};
            int[] numbers = {30, 10, 50, 20, 40};
            for (int i = 0; i < names.length; i++) {
                Person p = new Person();
                p.name = names[i];
                p.address = "Addr_" + i;
                p.social_number = numbers[i];
                orma.insertIntoPerson(p);
            }
            assertCondition("Setup: Inserted 5 unsorted records", orma.selectFromPerson().count() == 5);

            // --- Test 9a: ORDER BY social_number ASC ---
            // Expected order: 10, 20, 30, 40, 50 → Alice, Diana, Charlie, Eve, Bob
            List<Person> ascResult = orma.selectFromPerson().orderBySocial_numberAsc().toList();
            assertCondition("ORDER BY ASC: first is smallest", ascResult.get(0).social_number == 10);
            assertCondition("ORDER BY ASC: last is largest", ascResult.get(4).social_number == 50);
            assertCondition("ORDER BY ASC: correctly sorted", 
                ascResult.get(0).social_number <= ascResult.get(1).social_number &&
                ascResult.get(1).social_number <= ascResult.get(2).social_number &&
                ascResult.get(2).social_number <= ascResult.get(3).social_number &&
                ascResult.get(3).social_number <= ascResult.get(4).social_number);

            // --- Test 9b: ORDER BY social_number DESC ---
            // Expected order: 50, 40, 30, 20, 10 → Bob, Eve, Charlie, Diana, Alice
            List<Person> descResult = orma.selectFromPerson().orderBySocial_numberDesc().toList();
            assertCondition("ORDER BY DESC: first is largest", descResult.get(0).social_number == 50);
            assertCondition("ORDER BY DESC: last is smallest", descResult.get(4).social_number == 10);

            // --- Test 9c: ORDER BY name ASC (string sorting) ---
            // Expected alphabetical order: Alice, Bob, Charlie, Diana, Eve
            List<Person> nameAsc = orma.selectFromPerson().orderByNameAsc().toList();
            assertCondition("ORDER BY name ASC: alphabetical", "Alice".equals(nameAsc.get(0).name));
            assertCondition("ORDER BY name ASC: last alphabetically", "Eve".equals(nameAsc.get(4).name));

            // --- Test 9d: ORDER BY name DESC ---
            List<Person> nameDesc = orma.selectFromPerson().orderByNameDesc().toList();
            assertCondition("ORDER BY name DESC: reverse alphabetical", "Eve".equals(nameDesc.get(0).name));
            assertCondition("ORDER BY name DESC: last is first alphabetically", "Alice".equals(nameDesc.get(4).name));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("ORDER BY test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 10: Bulk Insert Performance
    // Inserts a large number of records (1000) and verifies all are stored
    // correctly. This tests memory handling, transaction efficiency, and
    // ensures no data loss at scale.
    // =========================================================================
    static void testBulkInsert(OrmaDatabase orma) {
        System.out.println("\n--- Test: Bulk Insert (1000 records) ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            int bulkCount = 1000;
            long startTime = System.currentTimeMillis();

            // Insert 1000 records
            for (int i = 0; i < bulkCount; i++) {
                Person p = new Person();
                p.name = "BulkUser_" + i;
                p.address = "Bulk Address " + i;
                p.social_number = i;
                orma.insertIntoPerson(p);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Verify all records were inserted
            int finalCount = orma.selectFromPerson().count();
            assertCondition("Bulk insert: all 1000 records stored", finalCount == bulkCount);

            // Verify a specific record in the middle (spot check)
            List<Person> spotCheck = orma.selectFromPerson().social_numberEq(500).toList();
            assertCondition("Bulk insert: spot check record 500", spotCheck.size() == 1 && "BulkUser_500".equals(spotCheck.get(0).name));

            // Verify the last record
            List<Person> lastCheck = orma.selectFromPerson().social_numberEq(999).toList();
            assertCondition("Bulk insert: last record exists", lastCheck.size() == 1);

            System.out.println("  [INFO] Bulk insert of " + bulkCount + " records took " + duration + "ms");
            // Performance sanity check: should complete in under 30 seconds
            assertCondition("Bulk insert performance acceptable (< 30s)", duration < 30000);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Bulk insert test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 11: Boundary Values
    // Tests extreme values to ensure the ORM handles integer limits, very
    // long strings, and SQL reserved words without corruption or crashes.
    // =========================================================================
    static void testBoundaryValues(OrmaDatabase orma) {
        System.out.println("\n--- Test: Boundary Values ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Test 11a: Integer.MAX_VALUE ---
            Person pMaxInt = new Person();
            pMaxInt.name = "MaxInt";
            pMaxInt.address = "test";
            pMaxInt.social_number = Integer.MAX_VALUE; // 2147483647
            long rowIdMax = orma.insertIntoPerson(pMaxInt);
            List<Person> maxResult = orma.selectFromPerson().idEq(rowIdMax).toList();
            assertCondition("Integer.MAX_VALUE preserved", maxResult.get(0).social_number == Integer.MAX_VALUE);

            // --- Test 11b: Integer.MIN_VALUE ---
            Person pMinInt = new Person();
            pMinInt.name = "MinInt";
            pMinInt.address = "test";
            pMinInt.social_number = Integer.MIN_VALUE; // -2147483648
            long rowIdMin = orma.insertIntoPerson(pMinInt);
            List<Person> minResult = orma.selectFromPerson().idEq(rowIdMin).toList();
            assertCondition("Integer.MIN_VALUE preserved", minResult.get(0).social_number == Integer.MIN_VALUE);

            // --- Test 11c: Zero ---
            Person pZero = new Person();
            pZero.name = "Zero";
            pZero.address = "test";
            pZero.social_number = 0;
            long rowIdZero = orma.insertIntoPerson(pZero);
            List<Person> zeroResult = orma.selectFromPerson().idEq(rowIdZero).toList();
            assertCondition("Zero value preserved", zeroResult.get(0).social_number == 0);

            // --- Test 11d: Very long string (10,000 characters) ---
            // This tests that TEXT columns handle large strings without truncation
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("A");
            }
            String longString = sb.toString();
            Person pLong = new Person();
            pLong.name = longString;
            pLong.address = "long string test";
            pLong.social_number = 42;
            long rowIdLong = orma.insertIntoPerson(pLong);
            List<Person> longResult = orma.selectFromPerson().idEq(rowIdLong).toList();
            assertCondition("10,000 char string preserved", longString.equals(longResult.get(0).name));
            assertCondition("10,000 char string length correct", longResult.get(0).name.length() == 10000);

            // --- Test 11e: SQL reserved words as data values ---
            // Ensures the ORM properly escapes/quotes reserved keywords
            Person pReserved = new Person();
            pReserved.name = "SELECT * FROM Person; DROP TABLE Person; --";
            pReserved.address = "INSERT INTO Person VALUES (1,2,3)";
            pReserved.social_number = 777;
            long rowIdReserved = orma.insertIntoPerson(pReserved);
            List<Person> reservedResult = orma.selectFromPerson().idEq(rowIdReserved).toList();
            assertCondition("SQL reserved words stored safely", "SELECT * FROM Person; DROP TABLE Person; --".equals(reservedResult.get(0).name));

            // --- Test 11f: Negative numbers ---
            Person pNeg = new Person();
            pNeg.name = "Negative";
            pNeg.address = "test";
            pNeg.social_number = -1;
            long rowIdNeg = orma.insertIntoPerson(pNeg);
            List<Person> negResult = orma.selectFromPerson().idEq(rowIdNeg).toList();
            assertCondition("Negative number preserved", negResult.get(0).social_number == -1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Boundary values test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 12: Binary Data as Base64
    // Since Sorma2 uses TEXT columns (no native BLOB), we store binary data
    // as Base64-encoded strings. This test generates random byte arrays,
    // encodes them, stores them, reads them back, decodes, and verifies
    // byte-for-byte integrity.
    // =========================================================================
    static void testBinaryDataAsBase64(OrmaDatabase orma) {
        System.out.println("\n--- Test: Binary Data as Base64 ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            Random rng = new Random(42); // Fixed seed for reproducibility

            // --- Test 12a: Small binary payload (16 bytes) ---
            byte[] smallBytes = new byte[16];
            rng.nextBytes(smallBytes);
            String smallBase64 = Base64.getEncoder().encodeToString(smallBytes);

            Person pSmall = new Person();
            pSmall.name = smallBase64;
            pSmall.address = "small binary";
            pSmall.social_number = 1;
            long rowIdSmall = orma.insertIntoPerson(pSmall);

            List<Person> smallResult = orma.selectFromPerson().idEq(rowIdSmall).toList();
            byte[] decodedSmall = Base64.getDecoder().decode(smallResult.get(0).name);
            assertCondition("16-byte binary round-trip", java.util.Arrays.equals(smallBytes, decodedSmall));

            // --- Test 12b: Medium binary payload (1024 bytes) ---
            byte[] medBytes = new byte[1024];
            rng.nextBytes(medBytes);
            String medBase64 = Base64.getEncoder().encodeToString(medBytes);

            Person pMed = new Person();
            pMed.name = medBase64;
            pMed.address = "medium binary";
            pMed.social_number = 2;
            long rowIdMed = orma.insertIntoPerson(pMed);

            List<Person> medResult = orma.selectFromPerson().idEq(rowIdMed).toList();
            byte[] decodedMed = Base64.getDecoder().decode(medResult.get(0).name);
            assertCondition("1KB binary round-trip", java.util.Arrays.equals(medBytes, decodedMed));

            // --- Test 12c: Large binary payload (64KB) ---
            // Tests that large Base64 strings don't get truncated
            byte[] largeBytes = new byte[65536];
            rng.nextBytes(largeBytes);
            String largeBase64 = Base64.getEncoder().encodeToString(largeBytes);

            Person pLarge = new Person();
            pLarge.name = largeBase64;
            pLarge.address = "large binary";
            pLarge.social_number = 3;
            long rowIdLarge = orma.insertIntoPerson(pLarge);

            List<Person> largeResult = orma.selectFromPerson().idEq(rowIdLarge).toList();
            byte[] decodedLarge = Base64.getDecoder().decode(largeResult.get(0).name);
            assertCondition("64KB binary round-trip", java.util.Arrays.equals(largeBytes, decodedLarge));
            assertCondition("64KB binary length preserved", decodedLarge.length == 65536);

            // --- Test 12d: All-zeros binary ---
            // Edge case: all bytes are 0x00
            byte[] zeroBytes = new byte[256];
            java.util.Arrays.fill(zeroBytes, (byte) 0);
            String zeroBase64 = Base64.getEncoder().encodeToString(zeroBytes);

            Person pZero = new Person();
            pZero.name = zeroBase64;
            pZero.address = "all zeros";
            pZero.social_number = 4;
            long rowIdZero = orma.insertIntoPerson(pZero);

            List<Person> zeroResult = orma.selectFromPerson().idEq(rowIdZero).toList();
            byte[] decodedZero = Base64.getDecoder().decode(zeroResult.get(0).name);
            assertCondition("All-zero bytes round-trip", java.util.Arrays.equals(zeroBytes, decodedZero));

            // --- Test 12e: All-0xFF binary ---
            // Edge case: all bytes are 0xFF
            byte[] ffBytes = new byte[256];
            java.util.Arrays.fill(ffBytes, (byte) 0xFF);
            String ffBase64 = Base64.getEncoder().encodeToString(ffBytes);

            Person pFF = new Person();
            pFF.name = ffBase64;
            pFF.address = "all 0xFF";
            pFF.social_number = 5;
            long rowIdFF = orma.insertIntoPerson(pFF);

            List<Person> ffResult = orma.selectFromPerson().idEq(rowIdFF).toList();
            byte[] decodedFF = Base64.getDecoder().decode(ffResult.get(0).name);
            assertCondition("All-0xFF bytes round-trip", java.util.Arrays.equals(ffBytes, decodedFF));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Binary data test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 13: Random Unicode & Control Characters
    // Tests that the ORM correctly handles arbitrary Unicode code points,
    // including supplementary planes (emoji, rare CJK), control characters,
    // zero-width characters, and RTL/LTR marks.
    // =========================================================================
    static void testRandomUnicodeAndControlChars(OrmaDatabase orma) {
        System.out.println("\n--- Test: Random Unicode & Control Characters ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Test 13a: Random Unicode code points (including supplementary planes) ---
            // Generate a string with random valid Unicode code points
            Random rng = new Random(123);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                // Generate random code point in valid Unicode range (0 to 0x10FFFF)
                // Exclude surrogates (0xD800-0xDFFF) as they are not valid alone
                int cp;
                do {
                    cp = rng.nextInt(0x110000); // 0 to 0x10FFFF
                } while (cp >= 0xD800 && cp <= 0xDFFF); // Skip surrogate range
                sb.appendCodePoint(cp);
            }
            String randomUnicode = sb.toString();

            Person pUnicode = new Person();
            pUnicode.name = randomUnicode;
            pUnicode.address = "random unicode";
            pUnicode.social_number = 1;
            long rowIdUnicode = orma.insertIntoPerson(pUnicode);

            List<Person> unicodeResult = orma.selectFromPerson().idEq(rowIdUnicode).toList();
            assertCondition("500 random Unicode code points preserved", randomUnicode.equals(unicodeResult.get(0).name));
            assertCondition("Random Unicode length preserved", unicodeResult.get(0).name.length() == randomUnicode.length());

            // --- Test 13b: Zero-width and invisible characters ---
            // These characters are invisible but must be preserved
            String invisibleChars = "Hello\u200BWorld\u200C!\u200D\uFEFF\u2060";
            // \u200B = Zero Width Space
            // \u200C = Zero Width Non-Joiner
            // \u200D = Zero Width Joiner
            // \uFEFF = Byte Order Mark / Zero Width No-Break Space
            // \u2060 = Word Joiner

            Person pInvisible = new Person();
            pInvisible.name = invisibleChars;
            pInvisible.address = "invisible chars";
            pInvisible.social_number = 2;
            long rowIdInvisible = orma.insertIntoPerson(pInvisible);

            List<Person> invisibleResult = orma.selectFromPerson().idEq(rowIdInvisible).toList();
            assertCondition("Zero-width characters preserved", invisibleChars.equals(invisibleResult.get(0).name));

            // --- Test 13c: RTL and LTR text mixed ---
            // Tests bidirectional text handling
            String rtlLtr = "Hello \u0645\u0631\u062D\u0628\u0627 World \u05E9\u05DC\u05D5\u05DD End";
            // Contains Arabic and Hebrew mixed with English

            Person pRtl = new Person();
            pRtl.name = rtlLtr;
            pRtl.address = "bidi text";
            pRtl.social_number = 3;
            long rowIdRtl = orma.insertIntoPerson(pRtl);

            List<Person> rtlResult = orma.selectFromPerson().idEq(rowIdRtl).toList();
            assertCondition("RTL/LTR mixed text preserved", rtlLtr.equals(rtlResult.get(0).name));

            // --- Test 13d: Control characters (except NULL byte) ---
            // SQLite TEXT cannot reliably store 0x00, but other control chars should work
            StringBuilder controlSb = new StringBuilder();
            for (int i = 1; i < 32; i++) { // Skip 0x00 (NULL), include 0x01-0x1F
                controlSb.append((char) i);
            }
            String controlChars = controlSb.toString();

            Person pControl = new Person();
            pControl.name = controlChars;
            pControl.address = "control chars";
            pControl.social_number = 4;
            long rowIdControl = orma.insertIntoPerson(pControl);

            List<Person> controlResult = orma.selectFromPerson().idEq(rowIdControl).toList();
            assertCondition("Control characters (0x01-0x1F) preserved", controlChars.equals(controlResult.get(0).name));

            // --- Test 13e: Repeated emoji sequences (multi-codepoint graphemes) ---
            // Family emoji (multiple code points joined by ZWJ)
            String familyEmoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"; // 👨‍👩‍👧‍👦
            String repeatedEmoji = familyEmoji + familyEmoji + familyEmoji;

            Person pEmoji = new Person();
            pEmoji.name = repeatedEmoji;
            pEmoji.address = "complex emoji";
            pEmoji.social_number = 5;
            long rowIdEmoji = orma.insertIntoPerson(pEmoji);

            List<Person> emojiResult = orma.selectFromPerson().idEq(rowIdEmoji).toList();
            assertCondition("Complex emoji sequences preserved", repeatedEmoji.equals(emojiResult.get(0).name));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Random Unicode test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 14: Rapid Insert/Delete Cycles
    // Stress test that rapidly inserts and deletes records in a loop to
    // detect memory leaks, resource exhaustion, or database corruption
    // from repeated allocation/deallocation cycles.
    // =========================================================================
    static void testRapidInsertDeleteCycles(OrmaDatabase orma) {
        System.out.println("\n--- Test: Rapid Insert/Delete Cycles ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            int cycles = 100;        // Number of insert/delete cycles
            int batchSize = 50;      // Records per cycle
            boolean corruptionDetected = false;

            long startTime = System.currentTimeMillis();

            for (int cycle = 0; cycle < cycles; cycle++) {
                // Insert a batch of records
                for (int i = 0; i < batchSize; i++) {
                    Person p = new Person();
                    p.name = "Cycle_" + cycle + "_Item_" + i;
                    p.address = "addr";
                    p.social_number = cycle * batchSize + i;
                    orma.insertIntoPerson(p);
                }

                // Verify count is correct after insert
                int countAfterInsert = orma.selectFromPerson().count();
                if (countAfterInsert != batchSize) {
                    corruptionDetected = true;
                    break;
                }

                // Delete all records
                orma.deleteFromPerson().execute();

                // Verify count is 0 after delete
                int countAfterDelete = orma.selectFromPerson().count();
                if (countAfterDelete != 0) {
                    corruptionDetected = true;
                    break;
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertCondition("No corruption in " + cycles + " insert/delete cycles", !corruptionDetected);
            assertCondition("Final state is clean (0 records)", orma.selectFromPerson().count() == 0);
            System.out.println("  [INFO] " + cycles + " cycles x " + batchSize + " records took " + duration + "ms");
            // Performance sanity: should complete in under 60 seconds
            assertCondition("Rapid cycles performance acceptable (< 60s)", duration < 60000);

        } catch (Exception e) {
            assertCondition("Rapid insert/delete test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 15: Chained Query Conditions
    // Tests that multiple WHERE conditions can be chained together correctly.
    // Verifies that the generated SQL properly combines multiple AND clauses.
    // =========================================================================
    static void testChainedQueryConditions(OrmaDatabase orma) {
        System.out.println("\n--- Test: Chained Query Conditions ---");
        try {
            // Clean up and insert diverse test data
            orma.deleteFromPerson().execute();

            // Insert records with varied attributes for filtering
            Person p1 = new Person(); p1.name = "Alice";   p1.address = "NYC";     p1.social_number = 10; orma.insertIntoPerson(p1);
            Person p2 = new Person(); p2.name = "Bob";     p2.address = "NYC";     p2.social_number = 20; orma.insertIntoPerson(p2);
            Person p3 = new Person(); p3.name = "Charlie"; p3.address = "LA";      p3.social_number = 30; orma.insertIntoPerson(p3);
            Person p4 = new Person(); p4.name = "Diana";   p4.address = "LA";      p4.social_number = 40; orma.insertIntoPerson(p4);
            Person p5 = new Person(); p5.name = "Eve";     p5.address = "Chicago"; p5.social_number = 50; orma.insertIntoPerson(p5);

            assertCondition("Setup: 5 records inserted", orma.selectFromPerson().count() == 5);

            // --- Test 15a: Two conditions (address AND social_number) ---
            // Find people in NYC with social_number > 15
            // Should match only Bob (NYC, 20)
            List<Person> twoCond = orma.selectFromPerson()
                .addressEq("NYC")
                .social_numberGt(15)
                .toList();
            assertCondition("Two chained conditions: correct result", twoCond.size() == 1 && "Bob".equals(twoCond.get(0).name));

            // --- Test 15b: Three conditions ---
            // Find people in LA with social_number >= 30 and name LIKE 'D%'
            // Should match only Diana
            List<Person> threeCond = orma.selectFromPerson()
                .addressEq("LA")
                .social_numberGe(30)
                .nameLike("D%")
                .toList();
            assertCondition("Three chained conditions: correct result", threeCond.size() == 1 && "Diana".equals(threeCond.get(0).name));

            // --- Test 15c: Range + equality combined ---
            // social_number between 15 and 45 (exclusive: > 15 AND < 45) AND address = NYC
            // Should match only Bob (20, NYC)
            List<Person> rangeCond = orma.selectFromPerson()
                .social_numberBetween(15, 45)
                .addressEq("NYC")
                .toList();
            assertCondition("Range + equality: correct result", rangeCond.size() == 1 && "Bob".equals(rangeCond.get(0).name));

            // --- Test 15d: Condition that matches nothing ---
            // No one lives in "Atlantis"
            List<Person> noMatch = orma.selectFromPerson()
                .addressEq("Atlantis")
                .social_numberGt(0)
                .toList();
            assertCondition("Chained conditions with no match returns empty", noMatch.size() == 0);

            // --- Test 15e: All conditions match all records ---
            // social_number > 0 matches everyone
            List<Person> allMatch = orma.selectFromPerson()
                .social_numberGt(0)
                .nameIsNotNull()
                .toList();
            assertCondition("Broad conditions match all records", allMatch.size() == 5);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Chained query conditions test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 16: Concurrent Read/Write Integrity
    // A more aggressive concurrency test where writers insert known data
    // and readers verify data consistency in real-time. Ensures no partial
    // reads or corrupted data under concurrent access.
    // =========================================================================
    static void testConcurrentReadWriteIntegrity(OrmaDatabase orma) {
        System.out.println("\n--- Test: Concurrent Read/Write Integrity ---");
        try {
            // Enable WAL mode and set busy timeout for concurrency
            OrmaDatabase.run_query_for_single_result("PRAGMA journal_mode=WAL;");
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");

            // Clean up before test
            orma.deleteFromPerson().execute();

            int numWriters = 3;
            int numReaders = 5;
            int writesPerThread = 30;
            final AtomicInteger totalInserts = new AtomicInteger(0);
            final AtomicBoolean integrityViolation = new AtomicBoolean(false);
            final AtomicBoolean errorOccurred = new AtomicBoolean(false);

            Thread[] writers = new Thread[numWriters];
            Thread[] readers = new Thread[numReaders];

            // Writer threads: insert records with predictable data
            for (int i = 0; i < numWriters; i++) {
                final int writerId = i;
                writers[i] = new Thread(() -> {
                    for (int j = 0; j < writesPerThread; j++) {
                        try {
                            Person p = new Person();
                            // Use a predictable pattern so readers can verify
                            p.name = "W" + writerId + "_" + j;
                            p.address = "integrity_test";
                            p.social_number = writerId * 10000 + j;
                            orma.insertIntoPerson(p);
                            totalInserts.incrementAndGet();

                            // Small delay to interleave with readers
                            Thread.sleep(1);
                        } catch (Exception e) {
                            errorOccurred.set(true);
                        }
                    }
                });
            }

            // Reader threads: continuously read and verify data integrity
            for (int i = 0; i < numReaders; i++) {
                readers[i] = new Thread(() -> {
                    for (int j = 0; j < writesPerThread * 2; j++) {
                        try {
                            // Read all records and verify none have corrupted fields
                            List<Person> allRecords = orma.selectFromPerson()
                                .addressEq("integrity_test")
                                .toList();

                            for (Person record : allRecords) {
                                // Verify name follows expected pattern: W<id>_<num>
                                if (record.name != null && !record.name.startsWith("W")) {
                                    integrityViolation.set(true);
                                }
                                // Verify social_number is non-negative
                                if (record.social_number < 0) {
                                    integrityViolation.set(true);
                                }
                            }

                            Thread.sleep(2);
                        } catch (Exception e) {
                            // SQLITE_BUSY is acceptable under heavy load
                            String msg = getRootCauseMessage(e);
                            if (!msg.contains("SQLITE_BUSY") && !msg.contains("database is locked")) {
                                errorOccurred.set(true);
                            }
                        }
                    }
                });
            }

            // Start all threads
            for (Thread t : readers) t.start();
            for (Thread t : writers) t.start();

            // Wait for completion
            for (Thread t : writers) t.join();
            for (Thread t : readers) t.join();

            // Final verification: count should match total inserts
            int finalCount = orma.selectFromPerson().count();
            int expectedTotal = numWriters * writesPerThread;

            assertCondition("No integrity violations during concurrent access", !integrityViolation.get());
            assertCondition("No unexpected errors during concurrent access", !errorOccurred.get());
            assertCondition("Final count matches total inserts (" + finalCount + "/" + expectedTotal + ")", finalCount == expectedTotal);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Concurrent read/write integrity test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // HELPER: Get root cause message from an exception chain
    // Walks the cause chain to find the deepest error message.
    // =========================================================================
    static String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    // =========================================================================
    // HELPER: Simple assertion without external libraries
    // Prints [PASS] or [FAIL] and increments counters.
    // =========================================================================
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
