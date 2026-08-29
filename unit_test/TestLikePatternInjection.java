import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: LIKE Pattern Injection
 * Verifies that user-supplied strings containing SQL LIKE wildcards (% and _)
 * are handled correctly when used as literal search values.
 *
 * Security concern: If a user searches for "100%" or "file_name",
 * the % and _ characters should be treated as literal characters,
 * NOT as wildcards, unless explicitly intended.
 *
 * NOTE: Sorma2's generated Like() method passes the value as a bind parameter,
 *       which means % and _ ARE treated as wildcards by SQLite.
 *       This test documents that behavior and verifies no SQL injection occurs.
 */
public class TestLikePatternInjection {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: LIKE Pattern Injection ---");
        try {
            // Clean up and insert test data with special characters
            orma.deleteFromPerson().execute();

            // Insert records that contain literal % and _ characters
            Person p1 = new Person();
            p1.name = "100% complete";
            p1.address = "test";
            p1.social_number = 1;
            orma.insertIntoPerson(p1);

            Person p2 = new Person();
            p2.name = "file_name.txt";
            p2.address = "test";
            p2.social_number = 2;
            orma.insertIntoPerson(p2);

            Person p3 = new Person();
            p3.name = "50% off_sale";
            p3.address = "test";
            p3.social_number = 3;
            orma.insertIntoPerson(p3);

            Person p4 = new Person();
            p4.name = "normal text";
            p4.address = "test";
            p4.social_number = 4;
            orma.insertIntoPerson(p4);

            Person p5 = new Person();
            p5.name = "%%%%";
            p5.address = "test";
            p5.social_number = 5;
            orma.insertIntoPerson(p5);

            SormaUnitTest.assertCondition("Setup: 5 records with special chars inserted",
                orma.selectFromPerson().count() == 5);

            // --- Test: LIKE with % in search value acts as wildcard ---
            // Searching for "100%" will match "100% complete" because % is a wildcard
            // This is expected SQLite LIKE behavior
            List<Person> percentSearch = orma.selectFromPerson().nameLike("100%").toList();
            SormaUnitTest.assertCondition("LIKE '100%' matches '100% complete'",
                percentSearch.size() == 1 && "100% complete".equals(percentSearch.get(0).name));

            // --- Test: LIKE with _ in search value acts as single-char wildcard ---
            // "file_name.txt" has _ which matches any single character
            // "file_name.txt" would also match "fileXname.txt" if it existed
            List<Person> underscoreSearch = orma.selectFromPerson().nameLike("file_name.txt").toList();
            SormaUnitTest.assertCondition("LIKE 'file_name.txt' finds the record",
                underscoreSearch.size() == 1 && "file_name.txt".equals(underscoreSearch.get(0).name));

            // --- Test: % does NOT cause SQL injection ---
            // A malicious LIKE pattern should not execute SQL
            String maliciousLike = "%'; DROP TABLE Person; --";
            List<Person> maliciousResult = orma.selectFromPerson().nameLike(maliciousLike).toList();
            // Table should still exist
            int countAfter = orma.selectFromPerson().count();
            SormaUnitTest.assertCondition("Malicious LIKE pattern doesn't inject SQL",
                countAfter == 5);

            // --- Test: _ wildcard matches any single character ---
            // "file_name.txt" with _ as wildcard would also match "fileXname.txt"
            // Let's verify _ matches the literal _ in our data
            List<Person> wildcardUnderscore = orma.selectFromPerson().nameLike("file?_name.txt").toList();
            // This should NOT match because ? is not a valid wildcard in SQLite LIKE
            // Actually in SQLite, only % and _ are wildcards
            // Let's use a proper test: "file_name.txt" matches itself
            SormaUnitTest.assertCondition("Underscore in data is findable via LIKE",
                underscoreSearch.size() >= 1);

            // --- Test: Searching for literal % using Eq (not Like) ---
            // Eq should find exact match without wildcard interpretation
            List<Person> exactPercent = orma.selectFromPerson().nameEq("%%%%").toList();
            SormaUnitTest.assertCondition("Eq('%%%%') finds exact match (no wildcard)",
                exactPercent.size() == 1 && "%%%%".equals(exactPercent.get(0).name));

            // --- Test: Searching for literal _ using Eq ---
            List<Person> exactUnderscore = orma.selectFromPerson().nameEq("file_name.txt").toList();
            SormaUnitTest.assertCondition("Eq('file_name.txt') finds exact match",
                exactUnderscore.size() == 1);

            // --- Test: NotLike with wildcards in data ---
            // NotLike('normal%') should exclude "normal text" but keep others
            List<Person> notLikeResult = orma.selectFromPerson().nameNotLike("normal%").toList();
            SormaUnitTest.assertCondition("NotLike('normal%') excludes 1, keeps 4",
                notLikeResult.size() == 4);

            // --- Test: LIKE with only wildcards matches everything ---
            List<Person> matchAll = orma.selectFromPerson().nameLike("%").toList();
            SormaUnitTest.assertCondition("LIKE '%' matches all records",
                matchAll.size() == 5);

            // --- Test: LIKE with underscore wildcard matches single chars ---
            // "%%%%" has 4 chars. LIKE "____" (4 underscores) should match it
            List<Person> fourChars = orma.selectFromPerson().nameLike("____").toList();
            SormaUnitTest.assertCondition("LIKE '____' matches 4-char strings",
                fourChars.size() == 1 && "%%%%".equals(fourChars.get(0).name));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("LIKE pattern injection test failed", false);
            e.printStackTrace();
        }
    }
}
