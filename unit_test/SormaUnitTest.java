import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;
import java.io.File;

/**
 * Sorma2 Plain Java Unit Test Runner
 *
 * This is the main entry point. It initializes the database,
 * runs all test suites, and prints the final summary.
 * Common helper methods (assertCondition, getRootCauseMessage)
 * are defined here and used by all individual test files.
 */
public class SormaUnitTest {

    // Global test result counters (accessible from all test classes)
    public static int passed = 0;
    public static int failed = 0;

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
                    // Person table
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
        // Run all test suites
        // =============================================
        TestBasicCrud.run(orma);
        TestSqlInjection.run(orma);
        TestSpecialCharacters.run(orma);
        TestSqliteBusy.run(orma);
        TestHeavyThreading.run(orma);
        TestNullAndEmptyStrings.run(orma);
        TestUpdateOperations.run(orma);
        TestQueryOperators.run(orma);
        TestOrderBy.run(orma);
        TestBulkInsert.run(orma);
        TestBoundaryValues.run(orma);
        TestRawBytesText.run(orma);
        TestColumnNameMatching.run(orma);

        // MUST be last: this test shuts down and reopens the DB
        TestRapidOpenClose.run(orma);

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
    // HELPER: Simple assertion without external libraries
    // Prints [PASS] or [FAIL] and increments global counters.
    // =========================================================================
    public static void assertCondition(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            failed++;
        }
    }

    // =========================================================================
    // HELPER: Get root cause message from an exception chain
    // Walks the cause chain to find the deepest error message.
    // =========================================================================
    public static String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "";
    }
}
