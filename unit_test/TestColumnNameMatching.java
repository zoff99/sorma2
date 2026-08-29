import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.ColumnMatch;
import java.util.List;

/**
 * TEST: Column Name Matching Precision
 * Verifies that the generated query builder correctly targets specific
 * columns by exact name, and does NOT accidentally match columns with
 * similar prefixes (AB vs ABC vs ABCD).
 */
public class TestColumnNameMatching {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Column Name Matching Precision ---");
        try {
            orma.deleteFromColumnMatch().execute();

            // Setup: Insert rows with deliberately confusing swapped values
            ColumnMatch row1 = new ColumnMatch();
            row1.AB = "val_AB";
            row1.ABC = "val_ABC";
            row1.ABCD = "val_ABCD";
            row1.AB_int = 1;
            row1.ABC_int = 100;
            long id1 = orma.insertIntoColumnMatch(row1);

            ColumnMatch row2 = new ColumnMatch();
            row2.AB = "val_ABC";       // Confusing: "val_ABC" in column AB
            row2.ABC = "val_AB";       // Confusing: "val_AB" in column ABC
            row2.ABCD = "val_AB";
            row2.AB_int = 100;
            row2.ABC_int = 1;
            long id2 = orma.insertIntoColumnMatch(row2);

            ColumnMatch row3 = new ColumnMatch();
            row3.AB = "x";
            row3.ABC = "x";
            row3.ABCD = "x";
            row3.AB_int = 42;
            row3.ABC_int = 42;
            long id3 = orma.insertIntoColumnMatch(row3);

            SormaUnitTest.assertCondition("Setup: 3 ColumnMatch rows inserted",
                orma.selectFromColumnMatch().count() == 3);

            // --- ABEq targets ONLY column "AB" ---
            List<ColumnMatch> abResult = orma.selectFromColumnMatch().ABEq("val_AB").toList();
            SormaUnitTest.assertCondition("ABEq('val_AB') matches exactly 1 row", abResult.size() == 1);
            SormaUnitTest.assertCondition("ABEq('val_AB') returns correct row (id1)", abResult.get(0).id == id1);

            // --- ABCEq targets ONLY column "ABC" ---
            List<ColumnMatch> abcResult = orma.selectFromColumnMatch().ABCEq("val_AB").toList();
            SormaUnitTest.assertCondition("ABCEq('val_AB') matches exactly 1 row", abcResult.size() == 1);
            SormaUnitTest.assertCondition("ABCEq('val_AB') returns correct row (id2)", abcResult.get(0).id == id2);

            // --- ABCDEq targets ONLY column "ABCD" ---
            List<ColumnMatch> abcdResult = orma.selectFromColumnMatch().ABCDEq("val_ABCD").toList();
            SormaUnitTest.assertCondition("ABCDEq('val_ABCD') matches exactly 1 row", abcdResult.size() == 1);
            SormaUnitTest.assertCondition("ABCDEq('val_ABCD') returns correct row (id1)", abcdResult.get(0).id == id1);

            // --- AB_intEq targets ONLY "AB_int", not "ABC_int" ---
            List<ColumnMatch> abIntResult = orma.selectFromColumnMatch().AB_intEq(1).toList();
            SormaUnitTest.assertCondition("AB_intEq(1) matches exactly 1 row", abIntResult.size() == 1);
            SormaUnitTest.assertCondition("AB_intEq(1) returns row1 (not row2)", abIntResult.get(0).id == id1);

            // --- ABC_intEq targets ONLY "ABC_int", not "AB_int" ---
            List<ColumnMatch> abcIntResult = orma.selectFromColumnMatch().ABC_intEq(1).toList();
            SormaUnitTest.assertCondition("ABC_intEq(1) matches exactly 1 row", abcIntResult.size() == 1);
            SormaUnitTest.assertCondition("ABC_intEq(1) returns row2 (not row1)", abcIntResult.get(0).id == id2);

            // --- Chained conditions with similar column names ---
            List<ColumnMatch> chainedResult = orma.selectFromColumnMatch()
                .ABEq("val_ABC")
                .ABCEq("val_AB")
                .toList();
            SormaUnitTest.assertCondition("Chained AB+ABC targets correct columns", chainedResult.size() == 1);
            SormaUnitTest.assertCondition("Chained result is row2", chainedResult.get(0).id == id2);

            // --- Cross-column mismatch returns nothing ---
            List<ColumnMatch> noMatch = orma.selectFromColumnMatch()
                .ABEq("val_ABC")
                .ABCEq("val_ABC")
                .toList();
            SormaUnitTest.assertCondition("Cross-column mismatch returns 0 rows", noMatch.size() == 0);

            // --- UPDATE targets correct column ---
            orma.updateColumnMatch().AB("UPDATED").AB_intEq(42).execute();
            List<ColumnMatch> afterUpdate = orma.selectFromColumnMatch().idEq(id3).toList();
            SormaUnitTest.assertCondition("UPDATE targets correct column (AB changed)",
                "UPDATED".equals(afterUpdate.get(0).AB));
            SormaUnitTest.assertCondition("UPDATE does not affect ABC column",
                "x".equals(afterUpdate.get(0).ABC));
            SormaUnitTest.assertCondition("UPDATE does not affect ABCD column",
                "x".equals(afterUpdate.get(0).ABCD));

            // --- LIKE on specific column doesn't cross-match ---
            List<ColumnMatch> likeAB = orma.selectFromColumnMatch().ABLike("val_%").toList();
            SormaUnitTest.assertCondition("ABLike('val_%') matches 2 rows", likeAB.size() == 2);

            // ABCD LIKE 'val_ABC%' matches only row1 (ABCD="val_ABCD"), not row2 (ABCD="val_AB")
            List<ColumnMatch> likeABCD = orma.selectFromColumnMatch().ABCDLike("val_ABC%").toList();
            SormaUnitTest.assertCondition("ABCDLike('val_ABC%') matches 1 row", likeABCD.size() == 1);
            SormaUnitTest.assertCondition("ABCDLike returns correct row (id1)", likeABCD.get(0).id == id1);

            // ABCD LIKE 'val_AB' matches only row2 (ABCD="val_AB")
            List<ColumnMatch> likeABCD2 = orma.selectFromColumnMatch().ABCDLike("val_AB").toList();
            SormaUnitTest.assertCondition("ABCDLike('val_AB') matches row2 only", likeABCD2.size() == 1);
            SormaUnitTest.assertCondition("ABCDLike('val_AB') returns row2", likeABCD2.get(0).id == id2);

            // --- NotEq on similar columns ---
            List<ColumnMatch> notEqResult = orma.selectFromColumnMatch().ABNotEq("val_AB").toList();
            SormaUnitTest.assertCondition("ABNotEq excludes only matching row", notEqResult.size() == 2);

            // Cleanup
            orma.deleteFromColumnMatch().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Column name matching test failed", false);
            e.printStackTrace();
        }
    }
}
