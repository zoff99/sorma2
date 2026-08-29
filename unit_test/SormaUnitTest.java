import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.io.File;

// FIXED: Import the inner interface from OrmaDatabase
import com.zoffcc.applications.sorm.OrmaDatabase.schema_upgrade_callback;

public class SormaUnitTest {
    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" Sorma2 Plain Java Unit Tests");
        System.out.println("========================================\n");

        String dbPath = "./unit_test_db.sqlite";
        new File(dbPath).delete(); // Clean slate

        OrmaDatabase orma = new OrmaDatabase(dbPath, "", false);

        // FIXED: Use the inner interface
        OrmaDatabase.set_schema_upgrade_callback(new schema_upgrade_callback() {
            @Override
            public void upgrade(int old_version, int new_version) {
                System.out.println(">> Schema Upgrade: " + old_version + " -> " + new_version);
                if (new_version >= 1) {
                    // Create the Person table (matches _sorm_Person.java definition)
                    OrmaDatabase.run_multi_sql(
                        "CREATE TABLE IF NOT EXISTS \"Person\" (\n" +
                        "  \"id\" INTEGER,\n" +
                        "  \"name\" TEXT,\n" +
                        "  \"address\" TEXT,\n" +
                        "  \"social_number\" INTEGER,\n" +
                        "  PRIMARY KEY(\"id\" AUTOINCREMENT)\n" +
                        ");"
                    );
                }
            }
        });

        try {
            OrmaDatabase.init(1);
        } catch (Exception e) {
            System.out.println("Note: Init exception: " + e.getMessage());
        }

        testBasicCrud(orma);
        testSqlInjectionSecurity(orma);
        testSpecialCharactersAndEncoding(orma);

        try { OrmaDatabase.shutdown(); } catch (Exception e) {}
        new File(dbPath).delete();

        System.out.println("\n========================================");
        System.out.println(" TEST SUMMARY");
        System.out.println("========================================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testBasicCrud(OrmaDatabase orma) {
        System.out.println("--- Test: Basic CRUD Operations ---");
        try {
            Person p = new Person();
            p.name = "John Doe";
            p.address = "123 Main St";
            p.social_number = 12345;
            
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert returns valid row ID", rowId > 0);

            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Query returned exactly 1 result", results.size() == 1);
            
            Person selected = results.get(0);
            assertCondition("Selected name matches", "John Doe".equals(selected.name));
            assertCondition("Selected int matches", selected.social_number == 12345);
            
            orma.deleteFromPerson().idEq(rowId).execute();
            int count = orma.selectFromPerson().count();
            assertCondition("Delete removes record", count == 0);
        } catch (Exception e) {
            assertCondition("CRUD operations threw no exceptions", false);
            e.printStackTrace();
        }
    }

    static void testSqlInjectionSecurity(OrmaDatabase orma) {
        System.out.println("\n--- Test: SQL Injection Security ---");
        try {
            String maliciousPayload = "Robert'); DROP TABLE Person; --";
            
            Person p = new Person();
            p.name = maliciousPayload;
            p.address = "Hell";
            p.social_number = 666;
            
            long rowId = orma.insertIntoPerson(p);
            assertCondition("Insert malicious payload", rowId > 0);

            int count = orma.selectFromPerson().count();
            assertCondition("Table still exists (Not dropped by injection!)", count >= 1);

            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            assertCondition("Payload stored exactly as literal string", maliciousPayload.equals(results.get(0).name));
            
        } catch (Exception e) {
            assertCondition("SQL Injection test failed (Vulnerability detected!)", false);
            e.printStackTrace();
        }
    }

    static void testSpecialCharactersAndEncoding(OrmaDatabase orma) {
        System.out.println("\n--- Test: Special Characters & Encoding ---");
        try {
            Person p = new Person();
            p.name = "Alice 😊🚀"; // Emojis
            p.address = "O'Connor's \"House\" \n \t \\"; // Quotes, newlines, escapes
            p.social_number = 999;
            
            long rowId = orma.insertIntoPerson(p);
            
            List<Person> results = orma.selectFromPerson().idEq(rowId).toList();
            Person selected = results.get(0);
            
            assertCondition("Emojis preserved (UTF-8 Check)", "Alice 😊🚀".equals(selected.name));
            assertCondition("Quotes and escapes preserved", "O'Connor's \"House\" \n \t \\".equals(selected.address));
            
        } catch (Exception e) {
            assertCondition("Special characters test failed", false);
            e.printStackTrace();
        }
    }

    static void assertCondition(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            failed++;
        }
    }
}