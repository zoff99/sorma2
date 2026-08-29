import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;
import java.util.List;
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

        // Use a temporary database file for testing (clean slate each run)
        String dbPath = "./unit_test_db.sqlite";
        new File(dbPath).delete(); // Delete any leftover DB from previous runs

        // Create the ORM database instance (unencrypted, WAL mode OFF initially)
        OrmaDatabase orma = new OrmaDatabase(dbPath, "", false);

        // CRITICAL: Define schema upgrade callback to create tables.
        // Sorma2 does NOT auto-create tables; you must provide CREATE TABLE statements.
        OrmaDatabase.set_schema_upgrade_callback(new schema_upgrade_callback() {
            @Override
            public void upgrade(int old_version, int new_version) {
                System.out.println(">> Schema Upgrade: " + old_version + " -> " + new_version);
                if (new_version >= 1) {
                    // Create the Person table (matches _sorm_Person.java definition)
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
            // Initialize the database at schema version 1
            OrmaDatabase.init(1);
        } catch (Exception e) {
            System.out.println("Note: Init exception: " + e.getMessage());
        }

        // =============================================
        // Run all test suites
        // =============================================

        // --- Original tests ---
        testBasicCrud(orma);
        testSqlInjectionSecurity(orma);
        testSpecialCharactersAndEncoding(orma);
        testSqliteBusy(orma);
        testHeavyThreading(orma);

        // --- New tests (added) ---
        testNullAndEmptyStrings(orma);
        testUpdateOperations(orma);
        testQueryOperators(orma);
        testOrderBy(orma);
        testBulkInsert(orma);
        testBoundaryValues(orma);

        // Shutdown the database and clean up the file
        try { OrmaDatabase.shutdown(); } catch (Exception e) {}
        new File(dbPath).delete();

        // Print final summary
        System.out.println("\n========================================");
        System.out.println(" TEST SUMMARY");
        System.out.println("========================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        
        // Exit with error code if any test failed (useful for CI/CD)
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
