import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Rapid Query Building Without Execution
 * Builds many query objects without executing them (no toList(), no execute()).
 * This tests for memory leaks in the query builder and verifies that
 * building queries has no side effects on the database.
 */
public class TestRapidQueryBuilding {

    static final int QUERY_COUNT = 1000;

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Rapid Query Building (" + QUERY_COUNT + " queries, no execution) ---");
        try {
            // Clean up and insert a small dataset
            orma.deleteFromPerson().execute();
            Person p = new Person();
            p.name = "QueryBuild_Test";
            p.address = "test";
            p.social_number = 42;
            orma.insertIntoPerson(p);

            int countBefore = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Setup: 1 record exists", countBefore == 1);

            // --- Phase 1: Build many SELECT queries without executing ---
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < QUERY_COUNT; i++) {
                // Build query but never call toList() or count()
                orma.selectFromPerson()
                    .nameEq("QueryBuild_Test")
                    .social_numberGt(i)
                    .addressIsNotNull()
                    .orderByNameAsc();
            }
            long buildTime = System.currentTimeMillis() - startTime;

            // Verify building queries didn't modify the database
            int countAfterBuild = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Building SELECT queries doesn't modify DB",
                countAfterBuild == countBefore);

            // --- Phase 2: Build many UPDATE queries without executing ---
            for (int i = 0; i < QUERY_COUNT; i++) {
                // Build update query but never call execute()
                orma.updatePerson()
                    .name("Should_Not_Apply_" + i)
                    .social_numberGt(1000 + i);
            }

            // Verify data was NOT modified
            List<Person> unchanged = orma.selectFromPerson().nameEq("QueryBuild_Test").toList();
            SormaUnitTest.assertCondition("Building UPDATE queries doesn't modify DB",
                unchanged.size() == 1);

            // --- Phase 3: Build many DELETE queries without executing ---
            for (int i = 0; i < QUERY_COUNT; i++) {
                // Build delete query but never call execute()
                orma.deleteFromPerson()
                    .social_numberGt(9999 + i);
            }

            // Verify data was NOT deleted
            int countAfterDeleteBuild = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Building DELETE queries doesn't modify DB",
                countAfterDeleteBuild == countBefore);

            // --- Phase 4: Mix of query types ---
            for (int i = 0; i < QUERY_COUNT / 4; i++) {
                orma.selectFromPerson().nameLike("Pattern_" + i);
                orma.updatePerson().address("Addr_" + i).idEq(i);
                orma.deleteFromPerson().nameEq("Delete_" + i);
                orma.selectFromPerson().social_numberBetween(i, i + 100).count();
            }

            // Final verification: data is still intact
            int finalCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("DB unchanged after " + (QUERY_COUNT * 3 + QUERY_COUNT / 4) +
                " query builds", finalCount == countBefore);

            System.out.println("  [INFO] " + QUERY_COUNT + " SELECT queries built in " + buildTime + "ms");
            SormaUnitTest.assertCondition("Query building performance acceptable",
                buildTime < 10000);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Rapid query building test failed", false);
            e.printStackTrace();
        }
    }
}
