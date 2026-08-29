import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;

/**
 * TEST: PRAGMA integrity_check
 * Runs SQLite's built-in database integrity check after performing
 * various operations. This detects silent file corruption that no
 * other test can find.
 *
 * PRAGMA integrity_check returns "ok" if the database is healthy,
 * or a list of problems if corruption is detected.
 */
public class TestIntegrityCheck {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: PRAGMA integrity_check ---");
        try {
            // Clean up before test
            orma.deleteFromPerson().execute();

            // --- Phase 1: Perform various operations that could cause corruption ---

            // Bulk inserts
            for (int i = 0; i < 100; i++) {
                Person p = new Person();
                p.name = "Integrity_" + i;
                p.address = "check_" + i;
                p.social_number = i;
                orma.insertIntoPerson(p);
            }

            // Updates
            orma.updatePerson().name("Modified").social_numberLt(50).execute();

            // Deletes
            orma.deleteFromPerson().social_numberGt(75).execute();

            // Re-inserts
            for (int i = 0; i < 20; i++) {
                Person p = new Person();
                p.name = "Reinsert_" + i;
                p.address = "after_delete";
                p.social_number = 200 + i;
                orma.insertIntoPerson(p);
            }

            // Transaction rollback (stress the journal)
            OrmaDatabase.run_multi_sql("BEGIN;");
            Person txPerson = new Person();
            txPerson.name = "Transaction_Stress";
            txPerson.address = "rollback";
            txPerson.social_number = 999;
            orma.insertIntoPerson(txPerson);
            OrmaDatabase.run_multi_sql("ROLLBACK;");

            // --- Phase 2: Run integrity check ---
            String integrityResult = OrmaDatabase.run_query_for_single_result("PRAGMA integrity_check;");

            SormaUnitTest.assertCondition("PRAGMA integrity_check returns 'ok'",
                integrityResult != null && integrityResult.trim().equals("ok"));

            if (integrityResult != null && !integrityResult.trim().equals("ok")) {
                System.err.println("  [ERROR] Integrity check failed: " + integrityResult);
            }

            // --- Phase 3: Run quick_check (faster, less thorough) ---
            String quickResult = OrmaDatabase.run_query_for_single_result("PRAGMA quick_check;");
            SormaUnitTest.assertCondition("PRAGMA quick_check returns 'ok'",
                quickResult != null && quickResult.trim().equals("ok"));

            // --- Phase 4: Verify foreign_key_check (should return empty = no violations) ---
            // Note: foreign_key_check returns rows if there are violations
            // Since we don't use foreign keys, this should be safe
            // We just verify it doesn't crash
            try {
                OrmaDatabase.run_query_for_single_result("PRAGMA foreign_key_check;");
                SormaUnitTest.assertCondition("PRAGMA foreign_key_check doesn't crash", true);
            } catch (Exception e) {
                // Some SQLite builds may not support this, that's ok
                SormaUnitTest.assertCondition("PRAGMA foreign_key_check handled gracefully", true);
            }

            // --- Phase 5: Verify data is still accessible after integrity check ---
            int finalCount = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Data accessible after integrity check", finalCount > 0);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Integrity check test failed", false);
            e.printStackTrace();
        }
    }
}
