import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: COUNT with WHERE Conditions
 * Verifies that count() correctly applies filters and returns accurate
 * counts for various WHERE conditions.
 */
public class TestCountWithWhere {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: COUNT with WHERE Conditions ---");
        try {
            // Clean up and insert test data
            orma.deleteFromPerson().execute();

            // Insert 10 records with known distribution
            // 4 in "NYC", 3 in "LA", 2 in "Chicago", 1 in "Boston"
            String[] cities = {"NYC", "NYC", "NYC", "NYC", "LA", "LA", "LA", "Chicago", "Chicago", "Boston"};
            int[] numbers = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
            for (int i = 0; i < 10; i++) {
                Person p = new Person();
                p.name = "Count_" + i;
                p.address = cities[i];
                p.social_number = numbers[i];
                orma.insertIntoPerson(p);
            }
            SormaUnitTest.assertCondition("Setup: 10 records inserted",
                orma.selectFromPerson().count() == 10);

            // --- Total count (no filter) ---
            SormaUnitTest.assertCondition("COUNT total = 10",
                orma.selectFromPerson().count() == 10);

            // --- Count with Eq filter ---
            SormaUnitTest.assertCondition("COUNT addressEq('NYC') = 4",
                orma.selectFromPerson().addressEq("NYC").count() == 4);
            SormaUnitTest.assertCondition("COUNT addressEq('LA') = 3",
                orma.selectFromPerson().addressEq("LA").count() == 3);
            SormaUnitTest.assertCondition("COUNT addressEq('Chicago') = 2",
                orma.selectFromPerson().addressEq("Chicago").count() == 2);
            SormaUnitTest.assertCondition("COUNT addressEq('Boston') = 1",
                orma.selectFromPerson().addressEq("Boston").count() == 1);

            // --- Count with non-existent value ---
            SormaUnitTest.assertCondition("COUNT addressEq('Atlantis') = 0",
                orma.selectFromPerson().addressEq("Atlantis").count() == 0);

            // --- Count with comparison operators ---
            // social_number > 50: values 60,70,80,90,100 → 5 records
            SormaUnitTest.assertCondition("COUNT social_numberGt(50) = 5",
                orma.selectFromPerson().social_numberGt(50).count() == 5);

            // social_number <= 30: values 10,20,30 → 3 records
            SormaUnitTest.assertCondition("COUNT social_numberLe(30) = 3",
                orma.selectFromPerson().social_numberLe(30).count() == 3);

            // social_number Between(25, 75): strict > 25 AND < 75 → 30,40,50,60,70 → 5 records
            SormaUnitTest.assertCondition("COUNT Between(25,75) = 5",
                orma.selectFromPerson().social_numberBetween(25, 75).count() == 5);

            // --- Count with chained conditions ---
            // address = "NYC" AND social_number > 20 → 30,40 → 2 records
            SormaUnitTest.assertCondition("COUNT chained: NYC AND >20 = 2",
                orma.selectFromPerson().addressEq("NYC").social_numberGt(20).count() == 2);

            // address = "LA" AND social_number < 60 → 50 → 1 record
            SormaUnitTest.assertCondition("COUNT chained: LA AND <60 = 1",
                orma.selectFromPerson().addressEq("LA").social_numberLt(60).count() == 1);

            // --- Count with LIKE ---
            // name LIKE 'Count_%' → all 10
            SormaUnitTest.assertCondition("COUNT nameLike('Count_%') = 10",
                orma.selectFromPerson().nameLike("Count_%").count() == 10);

            // name LIKE '%_0' → Count_0 → 1 record
            SormaUnitTest.assertCondition("COUNT nameLike('%_0') = 1",
                orma.selectFromPerson().nameLike("%_0").count() == 1);

            // --- Count with IS NULL / IS NOT NULL ---
            // Insert a record with NULL name
            Person pNull = new Person();
            pNull.name = null;
            pNull.address = "NullCity";
            pNull.social_number = 999;
            orma.insertIntoPerson(pNull);

            SormaUnitTest.assertCondition("COUNT nameIsNotNull() = 10",
                orma.selectFromPerson().nameIsNotNull().count() == 10);
            SormaUnitTest.assertCondition("COUNT nameIsNull() = 1",
                orma.selectFromPerson().nameIsNull().count() == 1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("COUNT with WHERE test failed", false);
            e.printStackTrace();
        }
    }
}
