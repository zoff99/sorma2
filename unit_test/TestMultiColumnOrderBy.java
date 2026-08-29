import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Multi-Column ORDER BY
 * Verifies that sorting by multiple columns works correctly.
 * Tests: primary sort + secondary sort, mixed ASC/DESC,
 * and verification that secondary sort only applies within primary groups.
 */
public class TestMultiColumnOrderBy {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Multi-Column ORDER BY ---");
        try {
            // Clean up and insert test data
            orma.deleteFromPerson().execute();

            // Insert records with duplicate social_numbers to test secondary sort
            // Group 1: social_number=10, names: Charlie, Alice, Bob
            // Group 2: social_number=20, names: Diana, Eve
            // Group 3: social_number=30, name: Frank
            String[] names =     {"Charlie", "Alice", "Bob", "Diana", "Eve", "Frank"};
            int[]    numbers =   {10,        10,      10,    20,      20,   30};
            String[] addresses = {"Addr_C",  "Addr_A", "Addr_B", "Addr_D", "Addr_E", "Addr_F"};

            for (int i = 0; i < names.length; i++) {
                Person p = new Person();
                p.name = names[i];
                p.address = addresses[i];
                p.social_number = numbers[i];
                orma.insertIntoPerson(p);
            }
            SormaUnitTest.assertCondition("Setup: 6 records inserted",
                orma.selectFromPerson().count() == 6);

            // --- Test: ORDER BY social_number ASC, name ASC ---
            // Expected order within groups:
            //   10: Alice, Bob, Charlie
            //   20: Diana, Eve
            //   30: Frank
            List<Person> ascAsc = orma.selectFromPerson()
                .orderBySocial_numberAsc()
                .orderByNameAsc()
                .toList();

            SormaUnitTest.assertCondition("Multi-sort: first is Alice (10, A)",
                "Alice".equals(ascAsc.get(0).name) && ascAsc.get(0).social_number == 10);
            SormaUnitTest.assertCondition("Multi-sort: second is Bob (10, B)",
                "Bob".equals(ascAsc.get(1).name) && ascAsc.get(1).social_number == 10);
            SormaUnitTest.assertCondition("Multi-sort: third is Charlie (10, C)",
                "Charlie".equals(ascAsc.get(2).name) && ascAsc.get(2).social_number == 10);
            SormaUnitTest.assertCondition("Multi-sort: fourth is Diana (20, D)",
                "Diana".equals(ascAsc.get(3).name) && ascAsc.get(3).social_number == 20);
            SormaUnitTest.assertCondition("Multi-sort: fifth is Eve (20, E)",
                "Eve".equals(ascAsc.get(4).name) && ascAsc.get(4).social_number == 20);
            SormaUnitTest.assertCondition("Multi-sort: sixth is Frank (30, F)",
                "Frank".equals(ascAsc.get(5).name) && ascAsc.get(5).social_number == 30);

            // --- Test: ORDER BY social_number ASC, name DESC ---
            // Expected:
            //   10: Charlie, Bob, Alice (reverse alphabetical within group)
            //   20: Eve, Diana
            //   30: Frank
            List<Person> ascDesc = orma.selectFromPerson()
                .orderBySocial_numberAsc()
                .orderByNameDesc()
                .toList();

            SormaUnitTest.assertCondition("Multi-sort ASC,DESC: first is Charlie",
                "Charlie".equals(ascDesc.get(0).name));
            SormaUnitTest.assertCondition("Multi-sort ASC,DESC: second is Bob",
                "Bob".equals(ascDesc.get(1).name));
            SormaUnitTest.assertCondition("Multi-sort ASC,DESC: third is Alice",
                "Alice".equals(ascDesc.get(2).name));
            SormaUnitTest.assertCondition("Multi-sort ASC,DESC: fourth is Eve",
                "Eve".equals(ascDesc.get(3).name));
            SormaUnitTest.assertCondition("Multi-sort ASC,DESC: fifth is Diana",
                "Diana".equals(ascDesc.get(4).name));

            // --- Test: ORDER BY social_number DESC, name ASC ---
            // Expected:
            //   30: Frank
            //   20: Diana, Eve
            //   10: Alice, Bob, Charlie
            List<Person> descAsc = orma.selectFromPerson()
                .orderBySocial_numberDesc()
                .orderByNameAsc()
                .toList();

            SormaUnitTest.assertCondition("Multi-sort DESC,ASC: first is Frank (30)",
                "Frank".equals(descAsc.get(0).name) && descAsc.get(0).social_number == 30);
            SormaUnitTest.assertCondition("Multi-sort DESC,ASC: last is Charlie (10)",
                "Charlie".equals(descAsc.get(5).name) && descAsc.get(5).social_number == 10);

            // --- Test: ORDER BY name ASC, social_number ASC (name is primary) ---
            // All names are unique, so secondary sort doesn't matter here
            // But verify it doesn't break
            List<Person> namePrimary = orma.selectFromPerson()
                .orderByNameAsc()
                .orderBySocial_numberAsc()
                .toList();

            SormaUnitTest.assertCondition("Name-primary sort: first is Alice",
                "Alice".equals(namePrimary.get(0).name));
            SormaUnitTest.assertCondition("Name-primary sort: last is Frank",
                "Frank".equals(namePrimary.get(5).name));
            // Verify alphabetical order
            boolean alphaOrder = true;
            for (int i = 0; i < namePrimary.size() - 1; i++) {
                if (namePrimary.get(i).name.compareTo(namePrimary.get(i + 1).name) > 0) {
                    alphaOrder = false;
                    break;
                }
            }
            SormaUnitTest.assertCondition("Name-primary sort: alphabetical order verified", alphaOrder);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Multi-column ORDER BY test failed", false);
            e.printStackTrace();
        }
    }
}
