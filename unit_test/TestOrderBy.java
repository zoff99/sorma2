import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: ORDER BY
 * Verifies that the generated ORDER BY methods correctly sort results.
 */
public class TestOrderBy {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: ORDER BY ---");
        try {
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
            SormaUnitTest.assertCondition("Setup: Inserted 5 unsorted records", orma.selectFromPerson().count() == 5);

            // --- ORDER BY social_number ASC ---
            List<Person> ascResult = orma.selectFromPerson().orderBySocial_numberAsc().toList();
            SormaUnitTest.assertCondition("ORDER BY ASC: first is smallest", ascResult.get(0).social_number == 10);
            SormaUnitTest.assertCondition("ORDER BY ASC: last is largest", ascResult.get(4).social_number == 50);
            SormaUnitTest.assertCondition("ORDER BY ASC: correctly sorted",
                ascResult.get(0).social_number <= ascResult.get(1).social_number &&
                ascResult.get(1).social_number <= ascResult.get(2).social_number &&
                ascResult.get(2).social_number <= ascResult.get(3).social_number &&
                ascResult.get(3).social_number <= ascResult.get(4).social_number);

            // --- ORDER BY social_number DESC ---
            List<Person> descResult = orma.selectFromPerson().orderBySocial_numberDesc().toList();
            SormaUnitTest.assertCondition("ORDER BY DESC: first is largest", descResult.get(0).social_number == 50);
            SormaUnitTest.assertCondition("ORDER BY DESC: last is smallest", descResult.get(4).social_number == 10);

            // --- ORDER BY name ASC (string sorting) ---
            List<Person> nameAsc = orma.selectFromPerson().orderByNameAsc().toList();
            SormaUnitTest.assertCondition("ORDER BY name ASC: alphabetical", "Alice".equals(nameAsc.get(0).name));
            SormaUnitTest.assertCondition("ORDER BY name ASC: last alphabetically", "Eve".equals(nameAsc.get(4).name));

            // --- ORDER BY name DESC ---
            List<Person> nameDesc = orma.selectFromPerson().orderByNameDesc().toList();
            SormaUnitTest.assertCondition("ORDER BY name DESC: reverse alphabetical", "Eve".equals(nameDesc.get(0).name));
            SormaUnitTest.assertCondition("ORDER BY name DESC: last is first alphabetically", "Alice".equals(nameDesc.get(4).name));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("ORDER BY test failed", false);
            e.printStackTrace();
        }
    }
}
