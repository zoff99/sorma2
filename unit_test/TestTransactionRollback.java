import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Transaction Rollback
 * Verifies that SQLite transactions work correctly through the ORM.
 * Tests: BEGIN → INSERT → ROLLBACK → verify data is gone.
 * Also tests: BEGIN → INSERT → COMMIT → verify data persists.
 *
 * NOTE: We use raw SQL (BEGIN/COMMIT/ROLLBACK) via OrmaDatabase.run_multi_sql()
 *       to control transaction boundaries explicitly.
 */
public class TestTransactionRollback {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Transaction Rollback ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("Setup: table is empty",
                orma.selectFromPerson().count() == 0);

            // --- Test: BEGIN → INSERT → ROLLBACK → data should be gone ---
            // Start a transaction
            OrmaDatabase.run_multi_sql("BEGIN;");

            // Insert a record within the transaction
            Person p1 = new Person();
            p1.name = "Rollback_Test";
            p1.address = "should_disappear";
            p1.social_number = 111;
            orma.insertIntoPerson(p1);

            // Verify the record exists within the transaction (before rollback)
            int countDuringTx = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Record visible during transaction",
                countDuringTx == 1);

            // Rollback the transaction
            OrmaDatabase.run_multi_sql("ROLLBACK;");

            // Verify the record is GONE after rollback
            int countAfterRollback = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("ROLLBACK removes uncommitted data",
                countAfterRollback == 0);

            // --- Test: BEGIN → INSERT → COMMIT → data should persist ---
            OrmaDatabase.run_multi_sql("BEGIN;");

            Person p2 = new Person();
            p2.name = "Commit_Test";
            p2.address = "should_persist";
            p2.social_number = 222;
            orma.insertIntoPerson(p2);

            // Commit the transaction
            OrmaDatabase.run_multi_sql("COMMIT;");

            // Verify the record persists after commit
            int countAfterCommit = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("COMMIT persists data",
                countAfterCommit == 1);
            List<Person> committed = orma.selectFromPerson().nameEq("Commit_Test").toList();
            SormaUnitTest.assertCondition("Committed record is readable",
                committed.size() == 1 && "should_persist".equals(committed.get(0).address));

            // --- Test: BEGIN → multiple INSERTs → ROLLBACK → all gone ---
            OrmaDatabase.run_multi_sql("BEGIN;");

            for (int i = 0; i < 5; i++) {
                Person pMulti = new Person();
                pMulti.name = "MultiRollback_" + i;
                pMulti.address = "batch";
                pMulti.social_number = 300 + i;
                orma.insertIntoPerson(pMulti);
            }

            // Verify 5 + 1 (committed) = 6 records during transaction
            int countMulti = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Multiple inserts visible during transaction",
                countMulti == 6);

            // Rollback all 5 inserts
            OrmaDatabase.run_multi_sql("ROLLBACK;");

            // Only the previously committed record should remain
            int countAfterMultiRollback = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("ROLLBACK removes all batch inserts",
                countAfterMultiRollback == 1);

            // --- Test: BEGIN → UPDATE → ROLLBACK → original data restored ---
            // Update the committed record
            OrmaDatabase.run_multi_sql("BEGIN;");
            orma.updatePerson().name("Modified_Name").nameEq("Commit_Test").execute();

            // Verify modification is visible
            List<Person> modified = orma.selectFromPerson().nameEq("Modified_Name").toList();
            SormaUnitTest.assertCondition("UPDATE visible during transaction",
                modified.size() == 1);

            // Rollback the update
            OrmaDatabase.run_multi_sql("ROLLBACK;");

            // Verify original data is restored
            List<Person> restored = orma.selectFromPerson().nameEq("Commit_Test").toList();
            SormaUnitTest.assertCondition("ROLLBACK restores original data after UPDATE",
                restored.size() == 1);
            List<Person> modifiedGone = orma.selectFromPerson().nameEq("Modified_Name").toList();
            SormaUnitTest.assertCondition("Modified name no longer exists after ROLLBACK",
                modifiedGone.size() == 0);

            // --- Test: BEGIN → DELETE → ROLLBACK → data restored ---
            OrmaDatabase.run_multi_sql("BEGIN;");
            orma.deleteFromPerson().nameEq("Commit_Test").execute();

            // Verify deletion is visible
            SormaUnitTest.assertCondition("DELETE visible during transaction",
                orma.selectFromPerson().count() == 0);

            // Rollback the delete
            OrmaDatabase.run_multi_sql("ROLLBACK;");

            // Verify data is restored
            SormaUnitTest.assertCondition("ROLLBACK restores deleted data",
                orma.selectFromPerson().count() == 1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Transaction rollback test failed", false);
            e.printStackTrace();
            // Try to rollback any dangling transaction
            try { OrmaDatabase.run_multi_sql("ROLLBACK;"); } catch (Exception ignored) {}
        }
    }
}
