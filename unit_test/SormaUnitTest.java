import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import com.zoffcc.applications.sorm.ColumnMatch;
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;
import java.util.List;
import java.util.Random;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.nio.charset.StandardCharsets;
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
                    // Original Person table
                    OrmaDatabase.run_multi_sql(
                        "CREATE TABLE IF NOT EXISTS \"Person\" (\n" +
                        "  \"id\" INTEGER,\n" +
                        "  \"name\" TEXT,\n" +
                        "  \"address\" TEXT,\n" +
                        "  \"social_number\" INTEGER,\n" +
                        "  PRIMARY KEY(\"id\" AUTOINCREMENT)\n" +
                        ");"
                    );
                    // ColumnMatch table with confusingly similar column names
                    // Tests that AB does not match ABC, ABC does not match ABCD, etc.
                    OrmaDatabase.run_multi_sql(
                        "CREATE TABLE IF NOT EXISTS \"ColumnMatch\" (\n" +
                        "  \"id\" INTEGER,\n" +
                        "  \"AB\" TEXT,\n" +
                        "  \"ABC\" TEXT,\n" +
                        "  \"ABCD\" TEXT,\n" +
                        "  \"AB_int\" INTEGER,\n" +
                        "  \"ABC_int\" INTEGER,\n" +
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

        // =============================================
        // Extended tests (batch 3): raw binary through TEXT
        // =============================================
        testRawBytesThroughTextColumn(orma);

        // =============================================
        // Extended tests (batch 4): column name matching
        // =============================================
        testColumnNameMatching(orma);

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
    // TEST 17: Raw Bytes Through Sorma2 TEXT Column (ISO-8859-1)
    // Pushes actual raw binary data through Sorma2's TEXT column using
    // ISO-8859-1 encoding, which maps each byte (0x00-0xFF) directly to
    // the corresponding Unicode code point (U+0000-U+00FF).
    //
    // This test verifies:
    //   - Bytes 0x01-0xFF survive the round-trip through SQLite TEXT
    //   - NULL byte (0x00) behavior is documented (may be truncated)
    //   - Random binary payloads without NULL bytes are preserved
    //   - All 255 non-NULL byte values are stored and retrieved correctly
    //
    // NOTE: SQLite TEXT columns store UTF-8. ISO-8859-1 characters U+0080-U+00FF
    //       are multi-byte in UTF-8, but the mapping is reversible.
    //       The NULL byte (0x00) is a known limitation of TEXT columns.
    // =========================================================================
    static void testRawBytesThroughTextColumn(OrmaDatabase orma) {
        System.out.println("\n--- Test: Raw Bytes Through TEXT Column (ISO-8859-1) ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Test 17a: All non-NULL byte values (0x01 to 0xFF) ---
            // ISO-8859-1 maps each byte value directly to the same Unicode code point.
            // Byte 0x41 → char U+0041 ('A'), Byte 0xFF → char U+00FF ('ÿ')
            // We skip 0x00 because SQLite TEXT may truncate at NULL bytes.
            byte[] allBytesNoNull = new byte[255];
            for (int i = 0; i < 255; i++) {
                allBytesNoNull[i] = (byte) (i + 1); // 0x01, 0x02, ..., 0xFF
            }

            // Encode raw bytes as a String using ISO-8859-1 (1:1 byte-to-char mapping)
            String binaryAsString = new String(allBytesNoNull, StandardCharsets.ISO_8859_1);

            // Store through Sorma2's normal ORM API
            Person pAll = new Person();
            pAll.name = binaryAsString;
            pAll.address = "all bytes 0x01-0xFF";
            pAll.social_number = 1;
            long rowIdAll = orma.insertIntoPerson(pAll);
            assertCondition("Insert 255 raw bytes via ISO-8859-1", rowIdAll > 0);

            // Read back through Sorma2 and decode back to bytes
            List<Person> allResults = orma.selectFromPerson().idEq(rowIdAll).toList();
            String readString = allResults.get(0).name;
            byte[] readBytes = readString.getBytes(StandardCharsets.ISO_8859_1);

            assertCondition("All 255 non-NULL bytes survive TEXT round-trip",
                java.util.Arrays.equals(allBytesNoNull, readBytes));
            assertCondition("Byte array length preserved (255)",
                readBytes.length == 255);

            // Verify specific byte values survived correctly
            assertCondition("Byte 0x01 preserved", readBytes[0] == 0x01);
            assertCondition("Byte 0x7F preserved (DEL)", readBytes[126] == 0x7F);
            assertCondition("Byte 0x80 preserved (first multi-byte UTF-8)", readBytes[127] == (byte) 0x80);
            assertCondition("Byte 0xFF preserved", readBytes[254] == (byte) 0xFF);

            // --- Test 17b: NULL byte (0x00) behavior ---
            // SQLite TEXT columns may truncate at NULL bytes.
            // This test documents the behavior without asserting pass/fail.
            byte[] withNull = new byte[] { 0x41, 0x42, 0x00, 0x43, 0x44 }; // "AB\0CD"
            String nullString = new String(withNull, StandardCharsets.ISO_8859_1);

            Person pNull = new Person();
            pNull.name = nullString;
            pNull.address = "null byte test";
            pNull.social_number = 2;
            long rowIdNull = orma.insertIntoPerson(pNull);
            assertCondition("Insert with NULL byte doesn't crash", rowIdNull > 0);

            List<Person> nullResults = orma.selectFromPerson().idEq(rowIdNull).toList();
            String nullRead = nullResults.get(0).name;
            byte[] nullBytes = nullRead.getBytes(StandardCharsets.ISO_8859_1);

            boolean nullSurvived = java.util.Arrays.equals(withNull, nullBytes);
            if (nullSurvived) {
                System.out.println("  [INFO] NULL byte (0x00) survived in TEXT column");
            } else {
                System.out.println("  [INFO] NULL byte (0x00) was truncated/lost in TEXT column");
                System.out.println("  [INFO] Data after NULL: got " + nullBytes.length + " bytes, expected " + withNull.length);
                System.out.println("  [INFO] This is a known SQLite TEXT limitation - use BLOB for binary with 0x00");
            }
            // We only assert that it didn't crash, not that NULL survived
            assertCondition("NULL byte test completed without crash", true);

            // --- Test 17c: Random binary payload (2KB, no NULL bytes) ---
            // Generate random bytes excluding 0x00 to test realistic binary data
            Random rng = new Random(777);
            byte[] randomNoNull = new byte[2048];
            for (int i = 0; i < randomNoNull.length; i++) {
                int b;
                do {
                    b = rng.nextInt(256);
                } while (b == 0); // Skip NULL byte
                randomNoNull[i] = (byte) b;
            }

            // Encode and store through Sorma2
            String randomStr = new String(randomNoNull, StandardCharsets.ISO_8859_1);
            Person pRandom = new Person();
            pRandom.name = randomStr;
            pRandom.address = "random binary no-null";
            pRandom.social_number = 3;
            long rowIdRandom = orma.insertIntoPerson(pRandom);
            assertCondition("Insert 2KB random binary via TEXT", rowIdRandom > 0);

            // Read back and verify
            List<Person> randomResults = orma.selectFromPerson().idEq(rowIdRandom).toList();
            byte[] randomRead = randomResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);

            assertCondition("2KB random binary survives TEXT round-trip",
                java.util.Arrays.equals(randomNoNull, randomRead));
            assertCondition("2KB random binary length preserved",
                randomRead.length == 2048);

            // --- Test 17d: Repeated pattern binary (detects truncation) ---
            // A repeating pattern makes truncation or corruption obvious
            byte[] pattern = new byte[1024];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) ((i % 255) + 1); // Repeating 0x01-0xFF pattern
            }

            String patternStr = new String(pattern, StandardCharsets.ISO_8859_1);
            Person pPattern = new Person();
            pPattern.name = patternStr;
            pPattern.address = "repeating pattern";
            pPattern.social_number = 4;
            long rowIdPattern = orma.insertIntoPerson(pPattern);

            List<Person> patternResults = orma.selectFromPerson().idEq(rowIdPattern).toList();
            byte[] patternRead = patternResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);

            assertCondition("Repeating pattern binary preserved",
                java.util.Arrays.equals(pattern, patternRead));

            // Verify the pattern is actually repeating correctly
            boolean patternIntact = true;
            for (int i = 0; i < patternRead.length; i++) {
                if (patternRead[i] != (byte) ((i % 255) + 1)) {
                    patternIntact = false;
                    break;
                }
            }
            assertCondition("Pattern sequence verified byte-by-byte", patternIntact);

            // --- Test 17e: Binary in multiple fields simultaneously ---
            // Store different binary data in name and address fields
            byte[] nameBytes = new byte[512];
            byte[] addrBytes = new byte[512];
            rng.nextBytes(nameBytes);
            rng.nextBytes(addrBytes);
            // Remove NULL bytes from both
            for (int i = 0; i < nameBytes.length; i++) { if (nameBytes[i] == 0) nameBytes[i] = 1; }
            for (int i = 0; i < addrBytes.length; i++) { if (addrBytes[i] == 0) addrBytes[i] = 1; }

            Person pMulti = new Person();
            pMulti.name = new String(nameBytes, StandardCharsets.ISO_8859_1);
            pMulti.address = new String(addrBytes, StandardCharsets.ISO_8859_1);
            pMulti.social_number = 5;
            long rowIdMulti = orma.insertIntoPerson(pMulti);

            List<Person> multiResults = orma.selectFromPerson().idEq(rowIdMulti).toList();
            byte[] nameRead = multiResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            byte[] addrRead = multiResults.get(0).address.getBytes(StandardCharsets.ISO_8859_1);

            assertCondition("Binary in name field preserved", java.util.Arrays.equals(nameBytes, nameRead));
            assertCondition("Binary in address field preserved", java.util.Arrays.equals(addrBytes, addrRead));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            assertCondition("Raw bytes through TEXT test failed", false);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TEST 18: Column Name Matching Precision
    // Verifies that the generated query builder correctly targets specific
    // columns by exact name, and does NOT accidentally match columns with
    // similar prefixes.
    //
    // Table schema: AB, ABC, ABCD, AB_int, ABC_int
    // Tests that:
    //   - ABEq() only matches column "AB", not "ABC" or "ABCD"
    //   - ABCEq() only matches column "ABC", not "AB" or "ABCD"
    //   - ABCDEq() only matches column "ABCD"
    //   - AB_intEq() only matches "AB_int", not "ABC_int"
    //   - Filtering on one column does not affect rows matched by another
    // =========================================================================
    static void testColumnNameMatching(OrmaDatabase orma) {
        System.out.println("\n--- Test: Column Name Matching Precision ---");
        try {
            // Clean up before test
            orma.deleteFromColumnMatch().execute();

            // --- Setup: Insert rows where each column has a UNIQUE value ---
            // If column matching is wrong, queries will return unexpected results.
            //
            // Row 1: AB="val_AB", ABC="val_ABC", ABCD="val_ABCD", AB_int=1, ABC_int=100
            // Row 2: AB="val_ABC", ABC="val_AB", ABCD="val_AB", AB_int=100, ABC_int=1
            // Row 3: AB="x", ABC="x", ABCD="x", AB_int=42, ABC_int=42
            //
            // Row 2 is the tricky one: it has "val_ABC" in the AB column
            // and "val_AB" in the ABC column (swapped values).

            ColumnMatch row1 = new ColumnMatch();
            row1.AB = "val_AB";
            row1.ABC = "val_ABC";
            row1.ABCD = "val_ABCD";
            row1.AB_int = 1;
            row1.ABC_int = 100;
            long id1 = orma.insertIntoColumnMatch(row1);

            ColumnMatch row2 = new ColumnMatch();
            row2.AB = "val_ABC";       // Deliberately confusing: "val_ABC" in column AB
            row2.ABC = "val_AB";       // Deliberately confusing: "val_AB" in column ABC
            row2.ABCD = "val_AB";      // Same value as ABC
            row2.AB_int = 100;         // Swapped with ABC_int
            row2.ABC_int = 1;          // Swapped with AB_int
            long id2 = orma.insertIntoColumnMatch(row2);

            ColumnMatch row3 = new ColumnMatch();
            row3.AB = "x";
            row3.ABC = "x";
            row3.ABCD = "x";
            row3.AB_int = 42;
            row3.ABC_int = 42;
            long id3 = orma.insertIntoColumnMatch(row3);

            assertCondition("Setup: 3 ColumnMatch rows inserted", orma.selectFromColumnMatch().count() == 3);

            // --- Test 18a: ABEq targets ONLY column "AB" ---
            // Looking for AB = "val_AB" should match ONLY row1 (not row2 which has "val_ABC" in AB)
            List<ColumnMatch> abResult = orma.selectFromColumnMatch().ABEq("val_AB").toList();
            assertCondition("ABEq('val_AB') matches exactly 1 row", abResult.size() == 1);
            assertCondition("ABEq('val_AB') returns correct row (id1)", abResult.get(0).id == id1);

            // --- Test 18b: ABCEq targets ONLY column "ABC" ---
            // Looking for ABC = "val_AB" should match ONLY row2 (not row1 which has "val_AB" in AB column)
            List<ColumnMatch> abcResult = orma.selectFromColumnMatch().ABCEq("val_AB").toList();
            assertCondition("ABCEq('val_AB') matches exactly 1 row", abcResult.size() == 1);
            assertCondition("ABCEq('val_AB') returns correct row (id2)", abcResult.get(0).id == id2);

            // --- Test 18c: ABCDEq targets ONLY column "ABCD" ---
            // Looking for ABCD = "val_ABCD" should match ONLY row1
            List<ColumnMatch> abcdResult = orma.selectFromColumnMatch().ABCDEq("val_ABCD").toList();
            assertCondition("ABCDEq('val_ABCD') matches exactly 1 row", abcdResult.size() == 1);
            assertCondition("ABCDEq('val_ABCD') returns correct row (id1)", abcdResult.get(0).id == id1);

            // --- Test 18d: AB_intEq targets ONLY "AB_int", not "ABC_int" ---
            // AB_int = 1 should match ONLY row1 (row2 has AB_int=100, ABC_int=1)
            List<ColumnMatch> abIntResult = orma.selectFromColumnMatch().AB_intEq(1).toList();
            assertCondition("AB_intEq(1) matches exactly 1 row", abIntResult.size() == 1);
            assertCondition("AB_intEq(1) returns row1 (not row2)", abIntResult.get(0).id == id1);

            // --- Test 18e: ABC_intEq targets ONLY "ABC_int", not "AB_int" ---
            // ABC_int = 1 should match ONLY row2 (row1 has ABC_int=100, AB_int=1)
            List<ColumnMatch> abcIntResult = orma.selectFromColumnMatch().ABC_intEq(1).toList();
            assertCondition("ABC_intEq(1) matches exactly 1 row", abcIntResult.size() == 1);
            assertCondition("ABC_intEq(1) returns row2 (not row1)", abcIntResult.get(0).id == id2);

            // --- Test 18f: Chained conditions with similar column names ---
            // AB = "val_ABC" AND ABC = "val_AB" should match ONLY row2
            List<ColumnMatch> chainedResult = orma.selectFromColumnMatch()
                .ABEq("val_ABC")
                .ABCEq("val_AB")
                .toList();
            assertCondition("Chained AB+ABC targets correct columns", chainedResult.size() == 1);
            assertCondition("Chained result is row2", chainedResult.get(0).id == id2);

            // --- Test 18g: Query that should return NOTHING (cross-column mismatch) ---
            // AB = "val_ABC" AND ABC = "val_ABC" → no row has both
            List<ColumnMatch> noMatch = orma.selectFromColumnMatch()
                .ABEq("val_ABC")
                .ABCEq("val_ABC")
                .toList();
            assertCondition("Cross-column mismatch returns 0 rows", noMatch.size() == 0);

            // --- Test 18h: UPDATE targets correct column ---
            // Update only AB where AB_int = 42, verify ABC and ABCD are unchanged
            orma.updateColumnMatch().AB("UPDATED").AB_intEq(42).execute();
            List<ColumnMatch> afterUpdate = orma.selectFromColumnMatch().idEq(id3).toList();
            assertCondition("UPDATE targets correct column (AB changed)", "UPDATED".equals(afterUpdate.get(0).AB));
            assertCondition("UPDATE does not affect ABC column", "x".equals(afterUpdate.get(0).ABC));
            assertCondition("UPDATE does not affect ABCD column", "x".equals(afterUpdate.get(0).ABCD));

            // --- Test 18i: LIKE on specific column doesn't cross-match ---
            // AB LIKE 'val_%' should match row1 (AB="val_AB") and row2 (AB="val_ABC")
            List<ColumnMatch> likeAB = orma.selectFromColumnMatch().ABLike("val_%").toList();
            assertCondition("ABLike('val_%') matches 2 rows", likeAB.size() == 2);

            // ABCD LIKE 'val_ABC%' should match ONLY row1 (ABCD="val_ABCD")
            // Row2 has ABCD="val_AB" which does NOT match "val_ABC%"
            List<ColumnMatch> likeABCD = orma.selectFromColumnMatch().ABCDLike("val_ABC%").toList();
            assertCondition("ABCDLike('val_ABC%') matches 1 row", likeABCD.size() == 1);
            assertCondition("ABCDLike returns correct row (id1)", likeABCD.get(0).id == id1);

            // ABCD LIKE 'val_AB' should match ONLY row2 (ABCD="val_AB")
            // This proves LIKE targets ABCD column specifically, not AB column
            List<ColumnMatch> likeABCD2 = orma.selectFromColumnMatch().ABCDLike("val_AB").toList();
            assertCondition("ABCDLike('val_AB') matches row2 only", likeABCD2.size() == 1);
            assertCondition("ABCDLike('val_AB') returns row2", likeABCD2.get(0).id == id2);

            // --- Test 18j: NotEq on similar columns ---
            // AB NotEq "val_AB" should return row2 and row3 (not row1)
            List<ColumnMatch> notEqResult = orma.selectFromColumnMatch().ABNotEq("val_AB").toList();
            assertCondition("ABNotEq excludes only matching row", notEqResult.size() == 2);

            // Cleanup
            orma.deleteFromColumnMatch().execute();

        } catch (Exception e) {
            assertCondition("Column name matching test failed", false);
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
