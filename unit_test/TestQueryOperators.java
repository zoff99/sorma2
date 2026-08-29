import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Query Operators
 * Tests all generated query builder comparison methods:
 * Eq, NotEq, Lt, Le, Gt, Ge, Like, NotLike, Between
 *
 * NOTE: Sorma2's Between() uses STRICT comparison (> and <), not >= and <=.
 */
public class TestQueryOperators {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Query Operators ---");
        try {
            orma.deleteFromPerson().execute();

            // Insert 5 records: social_number values 10, 20, 30, 40, 50
            for (int i = 1; i <= 5; i++) {
                Person p = new Person();
                p.name = "Person_" + i;
                p.address = "Address_" + i;
                p.social_number = i * 10;
                orma.insertIntoPerson(p);
            }
            SormaUnitTest.assertCondition("Setup: Inserted 5 test records", orma.selectFromPerson().count() == 5);

            // --- Eq ---
            List<Person> eqResult = orma.selectFromPerson().social_numberEq(30).toList();
            SormaUnitTest.assertCondition("Eq operator: finds exact match",
                eqResult.size() == 1 && "Person_3".equals(eqResult.get(0).name));

            // --- NotEq ---
            List<Person> notEqResult = orma.selectFromPerson().social_numberNotEq(30).toList();
            SormaUnitTest.assertCondition("NotEq operator: excludes match", notEqResult.size() == 4);

            // --- Lt ---
            List<Person> ltResult = orma.selectFromPerson().social_numberLt(30).toList();
            SormaUnitTest.assertCondition("Lt operator: social_number < 30", ltResult.size() == 2);

            // --- Le ---
            List<Person> leResult = orma.selectFromPerson().social_numberLe(30).toList();
            SormaUnitTest.assertCondition("Le operator: social_number <= 30", leResult.size() == 3);

            // --- Gt ---
            List<Person> gtResult = orma.selectFromPerson().social_numberGt(30).toList();
            SormaUnitTest.assertCondition("Gt operator: social_number > 30", gtResult.size() == 2);

            // --- Ge ---
            List<Person> geResult = orma.selectFromPerson().social_numberGe(30).toList();
            SormaUnitTest.assertCondition("Ge operator: social_number >= 30", geResult.size() == 3);

            // --- Between (STRICT: x > val1 AND x < val2) ---
            List<Person> betweenResult = orma.selectFromPerson().social_numberBetween(15, 45).toList();
            SormaUnitTest.assertCondition("Between operator: 15 < x < 45 matches 20,30,40", betweenResult.size() == 3);

            List<Person> betweenStrict = orma.selectFromPerson().social_numberBetween(20, 40).toList();
            SormaUnitTest.assertCondition("Between operator: strict bounds (only 30)",
                betweenStrict.size() == 1 && betweenStrict.get(0).social_number == 30);

            // --- Like ---
            List<Person> likeResult = orma.selectFromPerson().nameLike("Person_%").toList();
            SormaUnitTest.assertCondition("Like operator: matches pattern", likeResult.size() == 5);

            List<Person> likeSpecific = orma.selectFromPerson().nameLike("%_3").toList();
            SormaUnitTest.assertCondition("Like operator: matches specific pattern",
                likeSpecific.size() == 1 && "Person_3".equals(likeSpecific.get(0).name));

            // --- NotLike ---
            List<Person> notLikeResult = orma.selectFromPerson().nameNotLike("%_3").toList();
            SormaUnitTest.assertCondition("NotLike operator: excludes pattern", notLikeResult.size() == 4);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Query operators test failed", false);
            e.printStackTrace();
        }
    }
}
