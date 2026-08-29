import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: UPDATE Operations
 * Verifies that the generated UPDATE query builder works correctly.
 */
public class TestUpdateOperations {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: UPDATE Operations ---");
        try {
            orma.deleteFromPerson().execute();

            // Insert a test record
            Person p = new Person();
            p.name = "Original Name";
            p.address = "Original Address";
            p.social_number = 100;
            long rowId = orma.insertIntoPerson(p);
            SormaUnitTest.assertCondition("Setup: Insert test record", rowId > 0);

            // --- Single field update ---
            orma.updatePerson().name("Updated Name").idEq(rowId).execute();
            List<Person> afterUpdate = orma.selectFromPerson().idEq(rowId).toList();
            SormaUnitTest.assertCondition("Single field update works", "Updated Name".equals(afterUpdate.get(0).name));
            SormaUnitTest.assertCondition("Unmodified field preserved after update",
                "Original Address".equals(afterUpdate.get(0).address));

            // --- Multi-field update ---
            orma.updatePerson().name("Multi Update").address("New Address").social_number(200).idEq(rowId).execute();
            afterUpdate = orma.selectFromPerson().idEq(rowId).toList();
            SormaUnitTest.assertCondition("Multi-field update: name", "Multi Update".equals(afterUpdate.get(0).name));
            SormaUnitTest.assertCondition("Multi-field update: address", "New Address".equals(afterUpdate.get(0).address));
            SormaUnitTest.assertCondition("Multi-field update: social_number", afterUpdate.get(0).social_number == 200);

            // --- Conditional update (WHERE clause) ---
            Person p2 = new Person();
            p2.name = "Conditional Target";
            p2.address = "Target Addr";
            p2.social_number = 300;
            long rowId2 = orma.insertIntoPerson(p2);

            // Update only records where social_number > 250 (matches row2 only)
            orma.updatePerson().address("Conditionally Updated").social_numberGt(250).execute();
            List<Person> row1After = orma.selectFromPerson().idEq(rowId).toList();
            List<Person> row2After = orma.selectFromPerson().idEq(rowId2).toList();
            SormaUnitTest.assertCondition("Conditional update affected target row",
                "Conditionally Updated".equals(row2After.get(0).address));
            SormaUnitTest.assertCondition("Conditional update did NOT affect non-matching row",
                "New Address".equals(row1After.get(0).address));

            // --- Update non-existent row ---
            orma.updatePerson().name("Ghost").idEq(99999).execute();
            int countAfterGhost = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Update non-existent row doesn't crash or add rows", countAfterGhost == 2);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("UPDATE operations test failed", false);
            e.printStackTrace();
        }
    }
}
