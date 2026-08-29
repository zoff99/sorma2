import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: DELETE with WHERE Conditions
 * Verifies that DELETE operations correctly filter rows using WHERE clauses.
 * Tests: delete by column value, delete with comparison operators,
 * delete with chained conditions, delete all, delete non-existent.
 */
public class TestDeleteWithWhere {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: DELETE with WHERE Conditions ---");
        try {
            // Clean up and insert test data
            orma.deleteFromPerson().execute();

            // Insert 6 records with varied data
            for (int i = 1; i <= 6; i++) {
                Person p = new Person();
                p.name = "DeleteTest_" + i;
                p.address = (i <= 3) ? "GroupA" : "GroupB";
                p.social_number = i * 10; // 10, 20, 30, 40, 50, 60
                orma.insertIntoPerson(p);
            }
            SormaUnitTest.assertCondition("Setup: 6 records inserted",
                orma.selectFromPerson().count() == 6);

            // --- Delete by exact column value ---
            // Delete only the record with name = "DeleteTest_1"
            orma.deleteFromPerson().nameEq("DeleteTest_1").execute();
            SormaUnitTest.assertCondition("DELETE by nameEq removes 1 row",
                orma.selectFromPerson().count() == 5);
            // Verify the correct row was deleted
            List<Person> check1 = orma.selectFromPerson().nameEq("DeleteTest_1").toList();
            SormaUnitTest.assertCondition("Deleted row no longer exists", check1.size() == 0);

            // --- Delete with comparison operator (Gt) ---
            // Delete records where social_number > 50 (only DeleteTest_6 with 60)
            orma.deleteFromPerson().social_numberGt(50).execute();
            SormaUnitTest.assertCondition("DELETE with Gt removes correct rows",
                orma.selectFromPerson().count() == 4);

            // --- Delete with comparison operator (Lt) ---
            // Delete records where social_number < 30 (DeleteTest_2 with 20)
            orma.deleteFromPerson().social_numberLt(30).execute();
            SormaUnitTest.assertCondition("DELETE with Lt removes correct rows",
                orma.selectFromPerson().count() == 3);

            // --- Delete with chained conditions ---
            // Remaining: DeleteTest_3(30,GroupA), DeleteTest_4(40,GroupB), DeleteTest_5(50,GroupB)
            // Delete where address="GroupB" AND social_number >= 50 → only DeleteTest_5
            orma.deleteFromPerson().addressEq("GroupB").social_numberGe(50).execute();
            SormaUnitTest.assertCondition("DELETE with chained conditions removes 1 row",
                orma.selectFromPerson().count() == 2);
            // Verify DeleteTest_4 still exists (GroupB but social_number=40 < 50)
            List<Person> check4 = orma.selectFromPerson().nameEq("DeleteTest_4").toList();
            SormaUnitTest.assertCondition("Non-matching row in same group preserved",
                check4.size() == 1);

            // --- Delete by address (removes multiple rows) ---
            // Remaining: DeleteTest_3(GroupA), DeleteTest_4(GroupB)
            // Delete all GroupA → removes DeleteTest_3
            orma.deleteFromPerson().addressEq("GroupA").execute();
            SormaUnitTest.assertCondition("DELETE by address removes matching rows",
                orma.selectFromPerson().count() == 1);

            // --- Delete non-existent row (should not crash or affect count) ---
            orma.deleteFromPerson().nameEq("NonExistent").execute();
            SormaUnitTest.assertCondition("DELETE non-existent row doesn't crash",
                orma.selectFromPerson().count() == 1);

            // --- Delete all remaining rows ---
            orma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("DELETE all empties table",
                orma.selectFromPerson().count() == 0);

        } catch (Exception e) {
            SormaUnitTest.assertCondition("DELETE with WHERE test failed", false);
            e.printStackTrace();
        }
    }
}
