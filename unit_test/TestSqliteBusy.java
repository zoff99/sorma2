import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * TEST: SQLITE_BUSY Handling
 * Opens a second raw JDBC connection to lock the database, then verifies
 * that the ORM correctly throws a SQLITE_BUSY error when trying to write.
 */
public class TestSqliteBusy {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQLITE_BUSY Handling ---");
        Connection blockerConn = null;
        try {
            // Set busy timeout to 0 so the ORM fails immediately when locked
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 0;");

            // Open a second raw JDBC connection to the same database file
            Class.forName("org.sqlite.JDBC");
            blockerConn = DriverManager.getConnection("jdbc:sqlite:unit_test_db.sqlite");
            blockerConn.setAutoCommit(false);

            // Performing a write operation acquires a RESERVED lock
            Statement stmt = blockerConn.createStatement();
            stmt.executeUpdate("INSERT INTO Person (name, address, social_number) VALUES ('Blocker', 'Holding Lock', 0)");

            // Now try to insert via the ORM. Should fail with SQLITE_BUSY.
            Person p = new Person();
            p.name = "ShouldFail";
            p.address = "Busy";
            p.social_number = 999;

            boolean busyDetected = false;
            try {
                orma.insertIntoPerson(p);
            } catch (Exception e) {
                String msg = SormaUnitTest.getRootCauseMessage(e);
                if (msg.contains("SQLITE_BUSY") || msg.contains("database is locked")) {
                    busyDetected = true;
                }
            }

            SormaUnitTest.assertCondition("SQLITE_BUSY correctly thrown when DB is locked", busyDetected);

        } catch (Exception e) {
            e.printStackTrace();
            SormaUnitTest.assertCondition("SQLITE_BUSY test failed unexpectedly", false);
        } finally {
            // Cleanup: Release the lock
            if (blockerConn != null) {
                try { blockerConn.rollback(); blockerConn.close(); } catch (SQLException e) {}
            }
            // Restore busy timeout for subsequent tests
            OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 5000;");
        }
    }
}
