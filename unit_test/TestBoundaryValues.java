import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;

/**
 * TEST: Boundary Values
 * Tests extreme values: integer limits, very long strings, SQL reserved words.
 */
public class TestBoundaryValues {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Boundary Values ---");
        try {
            orma.deleteFromPerson().execute();

            // --- Integer.MAX_VALUE ---
            Person pMaxInt = new Person();
            pMaxInt.name = "MaxInt";
            pMaxInt.address = "test";
            pMaxInt.social_number = Integer.MAX_VALUE;
            long rowIdMax = orma.insertIntoPerson(pMaxInt);
            List<Person> maxResult = orma.selectFromPerson().idEq(rowIdMax).toList();
            SormaUnitTest.assertCondition("Integer.MAX_VALUE preserved",
                maxResult.get(0).social_number == Integer.MAX_VALUE);

            // --- Integer.MIN_VALUE ---
            Person pMinInt = new Person();
            pMinInt.name = "MinInt";
            pMinInt.address = "test";
            pMinInt.social_number = Integer.MIN_VALUE;
            long rowIdMin = orma.insertIntoPerson(pMinInt);
            List<Person> minResult = orma.selectFromPerson().idEq(rowIdMin).toList();
            SormaUnitTest.assertCondition("Integer.MIN_VALUE preserved",
                minResult.get(0).social_number == Integer.MIN_VALUE);

            // --- Zero ---
            Person pZero = new Person();
            pZero.name = "Zero";
            pZero.address = "test";
            pZero.social_number = 0;
            long rowIdZero = orma.insertIntoPerson(pZero);
            List<Person> zeroResult = orma.selectFromPerson().idEq(rowIdZero).toList();
            SormaUnitTest.assertCondition("Zero value preserved", zeroResult.get(0).social_number == 0);

            // --- Very long string (10,000 characters) ---
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) { sb.append("A"); }
            String longString = sb.toString();
            Person pLong = new Person();
            pLong.name = longString;
            pLong.address = "long string test";
            pLong.social_number = 42;
            long rowIdLong = orma.insertIntoPerson(pLong);
            List<Person> longResult = orma.selectFromPerson().idEq(rowIdLong).toList();
            SormaUnitTest.assertCondition("10,000 char string preserved", longString.equals(longResult.get(0).name));
            SormaUnitTest.assertCondition("10,000 char string length correct", longResult.get(0).name.length() == 10000);

            // --- SQL reserved words as data values ---
            Person pReserved = new Person();
            pReserved.name = "SELECT * FROM Person; DROP TABLE Person; --";
            pReserved.address = "INSERT INTO Person VALUES (1,2,3)";
            pReserved.social_number = 777;
            long rowIdReserved = orma.insertIntoPerson(pReserved);
            List<Person> reservedResult = orma.selectFromPerson().idEq(rowIdReserved).toList();
            SormaUnitTest.assertCondition("SQL reserved words stored safely",
                "SELECT * FROM Person; DROP TABLE Person; --".equals(reservedResult.get(0).name));

            // --- Negative numbers ---
            Person pNeg = new Person();
            pNeg.name = "Negative";
            pNeg.address = "test";
            pNeg.social_number = -1;
            long rowIdNeg = orma.insertIntoPerson(pNeg);
            List<Person> negResult = orma.selectFromPerson().idEq(rowIdNeg).toList();
            SormaUnitTest.assertCondition("Negative number preserved", negResult.get(0).social_number == -1);

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Boundary values test failed", false);
            e.printStackTrace();
        }
    }
}
