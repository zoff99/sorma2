import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: NULL and Empty String Handling
 * Verifies that NULL values and empty strings are correctly stored and
 * retrieved. This is a common source of ORM bugs.
 */
public class TestNullAndEmptyStrings {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: NULL & Empty String Handling ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Insert with NULL values ---
            Person pNull = new Person();
            pNull.name = null;
            pNull.address = null;
            pNull.social_number = 0;
            long rowIdNull = orma.insertIntoPerson(pNull);
            SormaUnitTest.assertCondition("Insert with NULL values succeeds", rowIdNull > 0);

            List<Person> nullResults = orma.selectFromPerson().idEq(rowIdNull).toList();
            SormaUnitTest.assertCondition("NULL row retrieved", nullResults.size() == 1);
            SormaUnitTest.assertCondition("NULL name preserved as null", nullResults.get(0).name == null);
            SormaUnitTest.assertCondition("NULL address preserved as null", nullResults.get(0).address == null);

            // --- Insert with empty strings ---
            Person pEmpty = new Person();
            pEmpty.name = "";
            pEmpty.address = "";
            pEmpty.social_number = 1;
            long rowIdEmpty = orma.insertIntoPerson(pEmpty);
            SormaUnitTest.assertCondition("Insert with empty strings succeeds", rowIdEmpty > 0);

            List<Person> emptyResults = orma.selectFromPerson().idEq(rowIdEmpty).toList();
            SormaUnitTest.assertCondition("Empty string row retrieved", emptyResults.size() == 1);
            SormaUnitTest.assertCondition("Empty string preserved (not NULL)", "".equals(emptyResults.get(0).name));

            // --- IS NULL query ---
            List<Person> isNullResults = orma.selectFromPerson().nameIsNull().toList();
            SormaUnitTest.assertCondition("IS NULL finds only NULL rows", isNullResults.size() == 1);

            // --- IS NOT NULL query ---
            List<Person> isNotNullResults = orma.selectFromPerson().nameIsNotNull().toList();
            SormaUnitTest.assertCondition("IS NOT NULL excludes NULL rows", isNotNullResults.size() == 1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("NULL/Empty string test failed", false);
            e.printStackTrace();
        }
    }
}
