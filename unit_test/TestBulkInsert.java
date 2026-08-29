import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Bulk Insert Performance
 * Inserts 1000 records and verifies all are stored correctly.
 */
public class TestBulkInsert {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Bulk Insert (1000 records) ---");
        try {
            orma.deleteFromPerson().execute();

            int bulkCount = 1000;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < bulkCount; i++) {
                Person p = new Person();
                p.name = "BulkUser_" + i;
                p.address = "Bulk Address " + i;
                p.social_number = i;
                orma.insertIntoPerson(p);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            int finalCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Bulk insert: all 1000 records stored", finalCount == bulkCount);

            // Spot check a record in the middle
            List<Person> spotCheck = orma.selectFromPerson().social_numberEq(500).toList();
            SormaUnitTest.assertCondition("Bulk insert: spot check record 500",
                spotCheck.size() == 1 && "BulkUser_500".equals(spotCheck.get(0).name));

            // Verify last record
            List<Person> lastCheck = orma.selectFromPerson().social_numberEq(999).toList();
            SormaUnitTest.assertCondition("Bulk insert: last record exists", lastCheck.size() == 1);

            System.out.println("  [INFO] Bulk insert of " + bulkCount + " records took " + duration + "ms");
            SormaUnitTest.assertCondition("Bulk insert performance acceptable (< 30s)", duration < 30000);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Bulk insert test failed", false);
            e.printStackTrace();
        }
    }
}
