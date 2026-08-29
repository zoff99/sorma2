import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.BoolTest;
import java.util.List;

/**
 * TEST: Boolean Column Handling
 * Verifies that boolean columns are correctly stored and retrieved.
 * SQLite stores BOOLEAN as INTEGER (0 = false, 1 = true).
 *
 * Tests:
 *   - true/false round-trip through INSERT and SELECT
 *   - Boolean in WHERE clauses (Eq true, Eq false)
 *   - Boolean NotEq filtering
 *   - Multiple boolean columns in the same row
 *   - Boolean combined with other types in chained queries
 *   - UPDATE boolean values
 *   - All boolean combinations (truth table)
 */
public class TestBooleanHandling {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Boolean Column Handling ---");
        try {
            // Clean up before test
            orma.deleteFromBoolTest().execute();

            // --- Test 18a: Insert with true values ---
            BoolTest pTrue = new BoolTest();
            pTrue.label = "all_true";
            pTrue.is_active = true;
            pTrue.is_deleted = true;
            pTrue.has_permission = true;
            pTrue.priority = 1;
            long idTrue = orma.insertIntoBoolTest(pTrue);
            SormaUnitTest.assertCondition("Insert with all-true booleans", idTrue > 0);

            // --- Test 18b: Insert with false values ---
            BoolTest pFalse = new BoolTest();
            pFalse.label = "all_false";
            pFalse.is_active = false;
            pFalse.is_deleted = false;
            pFalse.has_permission = false;
            pFalse.priority = 2;
            long idFalse = orma.insertIntoBoolTest(pFalse);
            SormaUnitTest.assertCondition("Insert with all-false booleans", idFalse > 0);

            // --- Test 18c: Insert with mixed boolean values ---
            BoolTest pMixed = new BoolTest();
            pMixed.label = "mixed";
            pMixed.is_active = true;
            pMixed.is_deleted = false;
            pMixed.has_permission = true;
            pMixed.priority = 3;
            long idMixed = orma.insertIntoBoolTest(pMixed);
            SormaUnitTest.assertCondition("Insert with mixed booleans", idMixed > 0);

            SormaUnitTest.assertCondition("Setup: 3 BoolTest rows inserted",
                orma.selectFromBoolTest().count() == 3);

            // --- Test 18d: Read back all-true row and verify ---
            List<BoolTest> trueResults = orma.selectFromBoolTest().idEq(idTrue).toList();
            SormaUnitTest.assertCondition("All-true row retrieved", trueResults.size() == 1);
            SormaUnitTest.assertCondition("is_active=true preserved", trueResults.get(0).is_active == true);
            SormaUnitTest.assertCondition("is_deleted=true preserved", trueResults.get(0).is_deleted == true);
            SormaUnitTest.assertCondition("has_permission=true preserved", trueResults.get(0).has_permission == true);

            // --- Test 18e: Read back all-false row and verify ---
            List<BoolTest> falseResults = orma.selectFromBoolTest().idEq(idFalse).toList();
            SormaUnitTest.assertCondition("All-false row retrieved", falseResults.size() == 1);
            SormaUnitTest.assertCondition("is_active=false preserved", falseResults.get(0).is_active == false);
            SormaUnitTest.assertCondition("is_deleted=false preserved", falseResults.get(0).is_deleted == false);
            SormaUnitTest.assertCondition("has_permission=false preserved", falseResults.get(0).has_permission == false);

            // --- Test 18f: Read back mixed row and verify ---
            List<BoolTest> mixedResults = orma.selectFromBoolTest().idEq(idMixed).toList();
            SormaUnitTest.assertCondition("Mixed row retrieved", mixedResults.size() == 1);
            SormaUnitTest.assertCondition("Mixed: is_active=true", mixedResults.get(0).is_active == true);
            SormaUnitTest.assertCondition("Mixed: is_deleted=false", mixedResults.get(0).is_deleted == false);
            SormaUnitTest.assertCondition("Mixed: has_permission=true", mixedResults.get(0).has_permission == true);

            // --- Test 18g: WHERE is_active = true ---
            // Should match: all_true (true) and mixed (true), NOT all_false (false)
            List<BoolTest> activeTrue = orma.selectFromBoolTest().is_activeEq(true).toList();
            SormaUnitTest.assertCondition("is_activeEq(true) matches 2 rows", activeTrue.size() == 2);

            // --- Test 18h: WHERE is_active = false ---
            // Should match: only all_false
            List<BoolTest> activeFalse = orma.selectFromBoolTest().is_activeEq(false).toList();
            SormaUnitTest.assertCondition("is_activeEq(false) matches 1 row", activeFalse.size() == 1);
            SormaUnitTest.assertCondition("is_activeEq(false) returns correct row",
                "all_false".equals(activeFalse.get(0).label));

            // --- Test 18i: WHERE is_deleted = true ---
            // Should match: only all_true
            List<BoolTest> deletedTrue = orma.selectFromBoolTest().is_deletedEq(true).toList();
            SormaUnitTest.assertCondition("is_deletedEq(true) matches 1 row", deletedTrue.size() == 1);
            SormaUnitTest.assertCondition("is_deletedEq(true) returns correct row",
                "all_true".equals(deletedTrue.get(0).label));

            // --- Test 18j: WHERE is_deleted = false ---
            // Should match: all_false and mixed
            List<BoolTest> deletedFalse = orma.selectFromBoolTest().is_deletedEq(false).toList();
            SormaUnitTest.assertCondition("is_deletedEq(false) matches 2 rows", deletedFalse.size() == 2);

            // --- Test 18k: Chained boolean conditions ---
            // is_active=true AND is_deleted=false → only "mixed"
            List<BoolTest> chainedBool = orma.selectFromBoolTest()
                .is_activeEq(true)
                .is_deletedEq(false)
                .toList();
            SormaUnitTest.assertCondition("Chained: active=true AND deleted=false matches 1",
                chainedBool.size() == 1);
            SormaUnitTest.assertCondition("Chained: returns 'mixed' row",
                "mixed".equals(chainedBool.get(0).label));

            // --- Test 18l: Boolean combined with integer in WHERE ---
            // is_active=true AND priority > 2 → only "mixed" (priority=3)
            List<BoolTest> boolAndInt = orma.selectFromBoolTest()
                .is_activeEq(true)
                .priorityGt(2)
                .toList();
            SormaUnitTest.assertCondition("Boolean + integer WHERE matches correctly",
                boolAndInt.size() == 1 && "mixed".equals(boolAndInt.get(0).label));

            // --- Test 18m: NotEq on boolean ---
            // is_active NotEq true → should return only all_false
            List<BoolTest> notEqTrue = orma.selectFromBoolTest().is_activeNotEq(true).toList();
            SormaUnitTest.assertCondition("is_activeNotEq(true) matches 1 row", notEqTrue.size() == 1);
            SormaUnitTest.assertCondition("is_activeNotEq(true) returns false row",
                "all_false".equals(notEqTrue.get(0).label));

            // --- Test 18n: UPDATE boolean value ---
            // Change is_active from false to true for "all_false" row
            orma.updateBoolTest().is_active(true).idEq(idFalse).execute();
            List<BoolTest> afterUpdate = orma.selectFromBoolTest().idEq(idFalse).toList();
            SormaUnitTest.assertCondition("UPDATE boolean false→true works",
                afterUpdate.get(0).is_active == true);
            // Verify other booleans unchanged
            SormaUnitTest.assertCondition("UPDATE doesn't affect is_deleted",
                afterUpdate.get(0).is_deleted == false);
            SormaUnitTest.assertCondition("UPDATE doesn't affect has_permission",
                afterUpdate.get(0).has_permission == false);

            // --- Test 18o: UPDATE boolean true→false ---
            orma.updateBoolTest().is_active(false).idEq(idFalse).execute();
            afterUpdate = orma.selectFromBoolTest().idEq(idFalse).toList();
            SormaUnitTest.assertCondition("UPDATE boolean true→false works",
                afterUpdate.get(0).is_active == false);

            // --- Test 18p: All boolean combinations (truth table) ---
            // Insert all 8 combinations of 3 booleans
            orma.deleteFromBoolTest().execute();
            int comboCount = 0;
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    for (int c = 0; c <= 1; c++) {
                        BoolTest combo = new BoolTest();
                        combo.label = "combo_" + a + b + c;
                        combo.is_active = (a == 1);
                        combo.is_deleted = (b == 1);
                        combo.has_permission = (c == 1);
                        combo.priority = comboCount;
                        orma.insertIntoBoolTest(combo);
                        comboCount++;
                    }
                }
            }
            SormaUnitTest.assertCondition("Truth table: 8 combinations inserted",
                orma.selectFromBoolTest().count() == 8);

            // Verify each combination reads back correctly
            boolean truthTableOk = true;
            List<BoolTest> allCombos = orma.selectFromBoolTest().orderByPriorityAsc().toList();
            for (int i = 0; i < 8; i++) {
                int expectedA = (i >> 2) & 1; // Extract bit for is_active
                int expectedB = (i >> 1) & 1; // Extract bit for is_deleted
                int expectedC = i & 1;         // Extract bit for has_permission

                BoolTest row = allCombos.get(i);
                if (row.is_active != (expectedA == 1) ||
                    row.is_deleted != (expectedB == 1) ||
                    row.has_permission != (expectedC == 1)) {
                    truthTableOk = false;
                    System.err.println("  [ERROR] Combo " + i + " mismatch: got (" +
                        row.is_active + "," + row.is_deleted + "," + row.has_permission +
                        ") expected (" + (expectedA==1) + "," + (expectedB==1) + "," + (expectedC==1) + ")");
                }
            }
            SormaUnitTest.assertCondition("Truth table: all 8 boolean combinations correct", truthTableOk);

            // --- Test 18q: Filter truth table by specific combination ---
            // Find combo where is_active=true, is_deleted=false, has_permission=true (101 = priority 5)
            List<BoolTest> specificCombo = orma.selectFromBoolTest()
                .is_activeEq(true)
                .is_deletedEq(false)
                .has_permissionEq(true)
                .toList();
            SormaUnitTest.assertCondition("Truth table filter: exact combo match",
                specificCombo.size() == 1 && specificCombo.get(0).priority == 5);

            // Cleanup
            orma.deleteFromBoolTest().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Boolean handling test failed", false);
            e.printStackTrace();
        }
    }
}
