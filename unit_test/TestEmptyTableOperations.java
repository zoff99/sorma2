import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Operations on Empty Table
 * Verifies that all ORM operations behave correctly (no crashes, no exceptions)
 * when performed on a table with zero rows.
 */
public class TestEmptyTableOperations {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Operations on Empty Table ---");
        try {
            // Ensure table is empty
            orma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("Setup: table is empty",
                orma.selectFromPerson().count() == 0);

            // --- SELECT on empty table ---
            List<Person> emptySelect = orma.selectFromPerson().toList();
            SormaUnitTest.assertCondition("SELECT on empty table returns empty list",
                emptySelect != null && emptySelect.size() == 0);

            // --- SELECT with WHERE on empty table ---
            List<Person> emptyWhere = orma.selectFromPerson().nameEq("Nobody").toList();
            SormaUnitTest.assertCondition("SELECT with WHERE on empty table returns empty",
                emptyWhere != null && emptyWhere.size() == 0);

            // --- COUNT on empty table ---
            int emptyCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("COUNT on empty table = 0", emptyCount == 0);

            // --- COUNT with WHERE on empty table ---
            int emptyCountWhere = orma.selectFromPerson().social_numberGt(0).count();
            SormaUnitTest.assertCondition("COUNT with WHERE on empty table = 0", emptyCountWhere == 0);

            // --- DELETE on empty table (should not crash) ---
            orma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("DELETE on empty table doesn't crash", true);

            // --- DELETE with WHERE on empty table ---
            orma.deleteFromPerson().nameEq("Nobody").execute();
            SormaUnitTest.assertCondition("DELETE with WHERE on empty table doesn't crash", true);

            // --- UPDATE on empty table (should not crash) ---
            orma.updatePerson().name("Ghost").execute();
            SormaUnitTest.assertCondition("UPDATE on empty table doesn't crash", true);

            // --- UPDATE with WHERE on empty table ---
            orma.updatePerson().name("Ghost").idEq(99999).execute();
            SormaUnitTest.assertCondition("UPDATE with WHERE on empty table doesn't crash", true);

            // --- ORDER BY on empty table ---
            List<Person> emptyOrder = orma.selectFromPerson().orderByNameAsc().toList();
            SormaUnitTest.assertCondition("ORDER BY on empty table returns empty list",
                emptyOrder != null && emptyOrder.size() == 0);

            // --- LIKE on empty table ---
            List<Person> emptyLike = orma.selectFromPerson().nameLike("%anything%").toList();
            SormaUnitTest.assertCondition("LIKE on empty table returns empty list",
                emptyLike != null && emptyLike.size() == 0);

            // --- IS NULL on empty table ---
            List<Person> emptyNull = orma.selectFromPerson().nameIsNull().toList();
            SormaUnitTest.assertCondition("IS NULL on empty table returns empty list",
                emptyNull != null && emptyNull.size() == 0);

            // --- Chained conditions on empty table ---
            List<Person> emptyChain = orma.selectFromPerson()
                .nameEq("x")
                .social_numberGt(0)
                .addressIsNotNull()
                .toList();
            SormaUnitTest.assertCondition("Chained conditions on empty table returns empty",
                emptyChain != null && emptyChain.size() == 0);

            // --- Verify table is still usable after empty operations ---
            Person p = new Person();
            p.name = "AfterEmpty";
            p.address = "test";
            p.social_number = 1;
            long rowId = orma.insertIntoPerson(p);
            SormaUnitTest.assertCondition("Table usable after empty operations", rowId > 0);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Empty table operations test failed", false);
            e.printStackTrace();
        }
    }
}
