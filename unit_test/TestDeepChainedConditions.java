import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Deep Chained Conditions (50+ AND clauses)
 * Chains a large number of WHERE conditions together to test:
 *   - SQL query length limits
 *   - Bind parameter limits (SQLite default: 999 or 32766)
 *   - Query builder performance with many conditions
 *   - Correctness of results with many filters
 */
public class TestDeepChainedConditions {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Deep Chained Conditions ---");
        try {
            // Clean up and insert test data
            orma.deleteFromPerson().execute();

            // Insert records that will match specific chained conditions
            Person target = new Person();
            target.name = "DeepChain_Target";
            target.address = "ChainAddr";
            target.social_number = 42;
            long targetId = orma.insertIntoPerson(target);

            // Insert decoy records that should be filtered out
            for (int i = 0; i < 10; i++) {
                Person decoy = new Person();
                decoy.name = "Decoy_" + i;
                decoy.address = "DecoyAddr_" + i;
                decoy.social_number = 100 + i;
                orma.insertIntoPerson(decoy);
            }

            SormaUnitTest.assertCondition("Setup: 11 records inserted",
                orma.selectFromPerson().count() == 11);

            // --- Test: Chain 10 conditions ---
            List<Person> chain10 = orma.selectFromPerson()
                .nameEq("DeepChain_Target")
                .addressEq("ChainAddr")
                .social_numberEq(42)
                .social_numberGt(0)
                .social_numberLt(100)
                .social_numberGe(42)
                .social_numberLe(42)
                .nameIsNotNull()
                .addressIsNotNull()
                .nameNotEq("Decoy_0")
                .toList();
            SormaUnitTest.assertCondition("10 chained conditions: finds target",
                chain10.size() == 1 && chain10.get(0).id == targetId);

            // --- Test: Chain 20 conditions ---
            List<Person> chain20 = orma.selectFromPerson()
                .nameEq("DeepChain_Target")
                .addressEq("ChainAddr")
                .social_numberEq(42)
                .social_numberGt(0)
                .social_numberGt(10)
                .social_numberGt(20)
                .social_numberGt(30)
                .social_numberGt(40)
                .social_numberLt(100)
                .social_numberLt(90)
                .social_numberLt(80)
                .social_numberLt(70)
                .social_numberLt(60)
                .social_numberLt(50)
                .social_numberGe(42)
                .social_numberLe(42)
                .nameIsNotNull()
                .addressIsNotNull()
                .nameNotEq("Decoy_0")
                .nameNotEq("Decoy_1")
                .toList();
            SormaUnitTest.assertCondition("20 chained conditions: finds target",
                chain20.size() == 1 && chain20.get(0).id == targetId);

            // --- Test: Chain 50 conditions ---
            // Build a query with 50 conditions programmatically
            // We chain multiple Gt/Lt conditions that are all satisfied by social_number=42
            var query50 = orma.selectFromPerson()
                .nameEq("DeepChain_Target")
                .addressEq("ChainAddr")
                .social_numberEq(42);

            // Add many Gt conditions (all true for 42)
            query50 = query50.social_numberGt(0);
            query50 = query50.social_numberGt(1);
            query50 = query50.social_numberGt(5);
            query50 = query50.social_numberGt(10);
            query50 = query50.social_numberGt(15);
            query50 = query50.social_numberGt(20);
            query50 = query50.social_numberGt(25);
            query50 = query50.social_numberGt(30);
            query50 = query50.social_numberGt(35);
            query50 = query50.social_numberGt(40);
            query50 = query50.social_numberGt(41);

            // Add many Lt conditions (all true for 42)
            query50 = query50.social_numberLt(100);
            query50 = query50.social_numberLt(90);
            query50 = query50.social_numberLt(80);
            query50 = query50.social_numberLt(70);
            query50 = query50.social_numberLt(60);
            query50 = query50.social_numberLt(50);
            query50 = query50.social_numberLt(45);
            query50 = query50.social_numberLt(44);
            query50 = query50.social_numberLt(43);

            // Add NotEq conditions
            query50 = query50.nameNotEq("Decoy_0");
            query50 = query50.nameNotEq("Decoy_1");
            query50 = query50.nameNotEq("Decoy_2");
            query50 = query50.nameNotEq("Decoy_3");
            query50 = query50.nameNotEq("Decoy_4");
            query50 = query50.nameNotEq("Decoy_5");
            query50 = query50.nameNotEq("Decoy_6");
            query50 = query50.nameNotEq("Decoy_7");
            query50 = query50.nameNotEq("Decoy_8");
            query50 = query50.nameNotEq("Decoy_9");

            // Add IsNotNull conditions
            query50 = query50.nameIsNotNull();
            query50 = query50.addressIsNotNull();
            query50 = query50.social_numberIsNotNull();

            // Add Ge/Le conditions
            query50 = query50.social_numberGe(42);
            query50 = query50.social_numberGe(40);
            query50 = query50.social_numberGe(30);
            query50 = query50.social_numberLe(42);
            query50 = query50.social_numberLe(50);
            query50 = query50.social_numberLe(60);

            // Add name LIKE conditions
            query50 = query50.nameLike("DeepChain_%");
            query50 = query50.nameLike("%Target");
            query50 = query50.nameLike("Deep%Target");

            // Total: 3 + 11 + 9 + 10 + 3 + 6 + 3 = 45 conditions
            // Add a few more to hit 50
            query50 = query50.addressLike("Chain%");
            query50 = query50.addressLike("%Addr");
            query50 = query50.addressNotEq("DecoyAddr_0");
            query50 = query50.addressNotEq("DecoyAddr_1");
            query50 = query50.addressIsNotNull();
            // Total: 50 conditions

            List<Person> chain50 = query50.toList();
            SormaUnitTest.assertCondition("50 chained conditions: finds target",
                chain50.size() == 1 && chain50.get(0).id == targetId);

            // --- Test: Chain with contradictory conditions returns empty ---
            List<Person> contradictory = orma.selectFromPerson()
                .social_numberGt(50)
                .social_numberLt(30)
                .toList();
            SormaUnitTest.assertCondition("Contradictory conditions return 0 rows",
                contradictory.size() == 0);

            // --- Test: COUNT with deep chain ---
            int chainCount = orma.selectFromPerson()
                .nameEq("DeepChain_Target")
                .social_numberGt(0)
                .social_numberLt(100)
                .nameIsNotNull()
                .addressIsNotNull()
                .count();
            SormaUnitTest.assertCondition("COUNT with chained conditions = 1", chainCount == 1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Deep chained conditions test failed", false);
            e.printStackTrace();
        }
    }
}
