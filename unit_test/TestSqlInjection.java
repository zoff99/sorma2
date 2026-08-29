import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: SQL Injection Security
 * Verifies that malicious SQL payloads are safely stored as literal strings
 * and NOT executed. This confirms the ORM uses PreparedStatement parameters.
 */
public class TestSqlInjection {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQL Injection Security ---");
        try {
            // Classic SQL injection payload that attempts to drop the table
            String maliciousPayload = "Robert'); DROP TABLE Person; --";

            Person p = new Person();
            p.name = maliciousPayload;
            p.address = "Hell";
            p.social_number = 666;

            // Insert the malicious payload
            long rowId = orma.insertIntoPerson(p);
            SormaUnitTest.assertCondition("Insert malicious payload", rowId > 0);

            // If vulnerable, the table would be dropped and this throws an exception
            int count = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Table still exists (Not dropped by injection!)", count >= 1);

            // Verify the payload was stored as a harmless literal string
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            SormaUnitTest.assertCondition("Payload stored exactly as literal string",
                maliciousPayload.equals(results.get(0).name));

        } catch (Exception e) {
            SormaUnitTest.assertCondition("SQL Injection test failed (Vulnerability detected!)", false);
            e.printStackTrace();
        }
    }
}

