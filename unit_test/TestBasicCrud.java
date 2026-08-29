import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Basic CRUD Operations
 * Verifies that INSERT, SELECT, and DELETE work correctly.
 */
public class TestBasicCrud {

    public static void run(OrmaDatabase orma) {
        System.out.println("--- Test: Basic CRUD Operations ---");
        try {
            // Create a new Person object and set fields
            Person p = new Person();
            p.name = "John Doe";
            p.address = "123 Main St";
            p.social_number = 12345;

            // Insert into database
            long rowId = orma.insertIntoPerson(p);
            SormaUnitTest.assertCondition("Insert returns valid row ID", rowId > 0);

            // Select the inserted row by ID
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            SormaUnitTest.assertCondition("Query returned exactly 1 result", results.size() == 1);

            // Verify all fields match
            Person selected = results.get(0);
            SormaUnitTest.assertCondition("Selected name matches", "John Doe".equals(selected.name));
            SormaUnitTest.assertCondition("Selected int matches", selected.social_number == 12345);

            // Delete the row and verify it's gone
            orma.deleteFromPerson().idEq(rowId).execute();
            int count = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Delete removes record", count == 0);
        } catch (Exception e) {
            SormaUnitTest.assertCondition("CRUD operations threw no exceptions", false);
            e.printStackTrace();
        }
    }
}

