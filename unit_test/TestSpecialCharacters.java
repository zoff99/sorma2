import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Special Characters & Encoding
 * Verifies that Unicode characters (emojis), quotes, newlines, and escape
 * characters are correctly stored and retrieved without corruption.
 */
public class TestSpecialCharacters {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Special Characters & Encoding ---");
        try {
            Person p = new Person();
            p.name = "Alice 😊🚀"; // Emojis (UTF-8 check)
            p.address = "O'Connor's \"House\" \n \t \\"; // Quotes, newlines, escapes
            p.social_number = 999;

            long rowId = orma.insertIntoPerson(p);

            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            Person selected = results.get(0);

            SormaUnitTest.assertCondition("Emojis preserved (UTF-8 Check)", "Alice 😊🚀".equals(selected.name));
            SormaUnitTest.assertCondition("Quotes and escapes preserved",
                "O'Connor's \"House\" \n \t \\".equals(selected.address));

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Special characters test failed", false);
            e.printStackTrace();
        }
    }
}
