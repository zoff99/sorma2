import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import com.zoffcc.applications.sorm.ColumnMatch;
import com.zoffcc.applications.sorm.BoolTest;
import java.util.List;

/**
 * TEST: Interleaved Multi-Table Operations
 * Alternates operations between Person, ColumnMatch, and BoolTest tables
 * in the same session. Verifies that:
 *   - Operations on one table don't affect another
 *   - Autoincrement IDs are independent per table
 *   - State doesn't leak between table operations
 *   - Queries target the correct table
 */
public class TestInterleavedMultiTable {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Interleaved Multi-Table Operations ---");
        try {
            // Clean up all tables
            orma.deleteFromPerson().execute();
            orma.deleteFromColumnMatch().execute();
            orma.deleteFromBoolTest().execute();

            // --- Phase 1: Interleaved inserts ---
            for (int i = 0; i < 10; i++) {
                // Insert into Person
                Person p = new Person();
                p.name = "Person_" + i;
                p.address = "person_addr";
                p.social_number = i;
                orma.insertIntoPerson(p);

                // Insert into ColumnMatch
                ColumnMatch cm = new ColumnMatch();
                cm.AB = "AB_" + i;
                cm.ABC = "ABC_" + i;
                cm.ABCD = "ABCD_" + i;
                cm.AB_int = i;
                cm.ABC_int = i * 10;
                orma.insertIntoColumnMatch(cm);

                // Insert into BoolTest
                BoolTest bt = new BoolTest();
                bt.label = "Bool_" + i;
                bt.is_active = (i % 2 == 0);
                bt.is_deleted = false;
                bt.has_permission = (i % 3 == 0);
                bt.priority = i;
                orma.insertIntoBoolTest(bt);
            }

            SormaUnitTest.assertCondition("Interleaved inserts: Person has 10",
                orma.selectFromPerson().count() == 10);
            SormaUnitTest.assertCondition("Interleaved inserts: ColumnMatch has 10",
                orma.selectFromColumnMatch().count() == 10);
            SormaUnitTest.assertCondition("Interleaved inserts: BoolTest has 10",
                orma.selectFromBoolTest().count() == 10);

            // --- Phase 2: Interleaved reads ---
            // Read from Person, then ColumnMatch, then BoolTest
            List<Person> personRead = orma.selectFromPerson().nameEq("Person_5").toList();
            List<ColumnMatch> cmRead = orma.selectFromColumnMatch().ABEq("AB_5").toList();
            List<BoolTest> btRead = orma.selectFromBoolTest().labelEq("Bool_5").toList();

            SormaUnitTest.assertCondition("Interleaved read: Person_5 found",
                personRead.size() == 1 && personRead.get(0).social_number == 5);
            SormaUnitTest.assertCondition("Interleaved read: AB_5 found",
                cmRead.size() == 1 && "ABC_5".equals(cmRead.get(0).ABC));
            SormaUnitTest.assertCondition("Interleaved read: Bool_5 found",
                btRead.size() == 1 && btRead.get(0).priority == 5);

            // --- Phase 3: Interleaved updates ---
            orma.updatePerson().name("Updated_Person").nameEq("Person_3").execute();
            orma.updateColumnMatch().AB("Updated_AB").ABEq("AB_3").execute();
            orma.updateBoolTest().label("Updated_Bool").labelEq("Bool_3").execute();

            SormaUnitTest.assertCondition("Interleaved update: Person updated",
                orma.selectFromPerson().nameEq("Updated_Person").toList().size() == 1);
            SormaUnitTest.assertCondition("Interleaved update: ColumnMatch updated",
                orma.selectFromColumnMatch().ABEq("Updated_AB").toList().size() == 1);
            SormaUnitTest.assertCondition("Interleaved update: BoolTest updated",
                orma.selectFromBoolTest().labelEq("Updated_Bool").toList().size() == 1);

            // --- Phase 4: Interleaved deletes ---
            orma.deleteFromPerson().nameEq("Person_0").execute();
            orma.deleteFromColumnMatch().ABEq("AB_0").execute();
            orma.deleteFromBoolTest().labelEq("Bool_0").execute();

            SormaUnitTest.assertCondition("Interleaved delete: Person count = 9",
                orma.selectFromPerson().count() == 9);
            SormaUnitTest.assertCondition("Interleaved delete: ColumnMatch count = 9",
                orma.selectFromColumnMatch().count() == 9);
            SormaUnitTest.assertCondition("Interleaved delete: BoolTest count = 9",
                orma.selectFromBoolTest().count() == 9);

            // --- Phase 5: Verify no cross-table contamination ---
            // Deleting from Person should NOT affect ColumnMatch or BoolTest
            orma.deleteFromPerson().execute();
            SormaUnitTest.assertCondition("Delete all Person doesn't affect ColumnMatch",
                orma.selectFromColumnMatch().count() == 9);
            SormaUnitTest.assertCondition("Delete all Person doesn't affect BoolTest",
                orma.selectFromBoolTest().count() == 9);

            // --- Phase 6: Verify autoincrement independence ---
            // Insert into Person after clearing it - ID should continue from where it left off
            Person newPerson = new Person();
            newPerson.name = "New_After_Clear";
            newPerson.address = "test";
            newPerson.social_number = 99;
            long newId = orma.insertIntoPerson(newPerson);
            SormaUnitTest.assertCondition("Autoincrement continues independently", newId > 10);

            // Cleanup all tables
            orma.deleteFromPerson().execute();
            orma.deleteFromColumnMatch().execute();
            orma.deleteFromBoolTest().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Interleaved multi-table test failed", false);
            e.printStackTrace();
        }
    }
}
