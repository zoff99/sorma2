import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Person;
import java.util.List;
import java.util.Random;
import java.nio.charset.StandardCharsets;

/**
 * TEST: Raw Bytes Through Sorma2 TEXT Column (ISO-8859-1)
 * Pushes actual raw binary data through Sorma2's TEXT column using
 * ISO-8859-1 encoding (1:1 byte-to-char mapping).
 * Documents that NULL byte (0x00) may be truncated in TEXT columns.
 */
public class TestRawBytesText {

    public static void run(OrmaDatabase orma) {
        System.out.println("\n--- Test: Raw Bytes Through TEXT Column (ISO-8859-1) ---");
        try {
            orma.deleteFromPerson().execute();

            // --- All non-NULL byte values (0x01 to 0xFF) ---
            byte[] allBytesNoNull = new byte[255];
            for (int i = 0; i < 255; i++) {
                allBytesNoNull[i] = (byte) (i + 1);
            }
            String binaryAsString = new String(allBytesNoNull, StandardCharsets.ISO_8859_1);

            Person pAll = new Person();
            pAll.name = binaryAsString;
            pAll.address = "all bytes 0x01-0xFF";
            pAll.social_number = 1;
            long rowIdAll = orma.insertIntoPerson(pAll);
            SormaUnitTest.assertCondition("Insert 255 raw bytes via ISO-8859-1", rowIdAll > 0);

            List<Person> allResults = orma.selectFromPerson().idEq(rowIdAll).toList();
            byte[] readBytes = allResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            SormaUnitTest.assertCondition("All 255 non-NULL bytes survive TEXT round-trip",
                java.util.Arrays.equals(allBytesNoNull, readBytes));
            SormaUnitTest.assertCondition("Byte array length preserved (255)", readBytes.length == 255);

            // Verify specific byte values
            SormaUnitTest.assertCondition("Byte 0x01 preserved", readBytes[0] == 0x01);
            SormaUnitTest.assertCondition("Byte 0x7F preserved (DEL)", readBytes[126] == 0x7F);
            SormaUnitTest.assertCondition("Byte 0x80 preserved (first multi-byte UTF-8)", readBytes[127] == (byte) 0x80);
            SormaUnitTest.assertCondition("Byte 0xFF preserved", readBytes[254] == (byte) 0xFF);

            // --- NULL byte (0x00) behavior ---
            byte[] withNull = new byte[] { 0x41, 0x42, 0x00, 0x43, 0x44 };
            String nullString = new String(withNull, StandardCharsets.ISO_8859_1);

            Person pNull = new Person();
            pNull.name = nullString;
            pNull.address = "null byte test";
            pNull.social_number = 2;
            long rowIdNull = orma.insertIntoPerson(pNull);
            SormaUnitTest.assertCondition("Insert with NULL byte doesn't crash", rowIdNull > 0);

            List<Person> nullResults = orma.selectFromPerson().idEq(rowIdNull).toList();
            byte[] nullBytes = nullResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            boolean nullSurvived = java.util.Arrays.equals(withNull, nullBytes);
            if (nullSurvived) {
                System.out.println("  [INFO] NULL byte (0x00) survived in TEXT column");
            } else {
                System.out.println("  [INFO] NULL byte (0x00) was truncated/lost in TEXT column");
                System.out.println("  [INFO] Got " + nullBytes.length + " bytes, expected " + withNull.length);
            }
            SormaUnitTest.assertCondition("NULL byte test completed without crash", true);

            // --- Random binary payload (2KB, no NULL bytes) ---
            Random rng = new Random(777);
            byte[] randomNoNull = new byte[2048];
            for (int i = 0; i < randomNoNull.length; i++) {
                int b;
                do { b = rng.nextInt(256); } while (b == 0);
                randomNoNull[i] = (byte) b;
            }

            String randomStr = new String(randomNoNull, StandardCharsets.ISO_8859_1);
            Person pRandom = new Person();
            pRandom.name = randomStr;
            pRandom.address = "random binary no-null";
            pRandom.social_number = 3;
            long rowIdRandom = orma.insertIntoPerson(pRandom);
            SormaUnitTest.assertCondition("Insert 2KB random binary via TEXT", rowIdRandom > 0);

            List<Person> randomResults = orma.selectFromPerson().idEq(rowIdRandom).toList();
            byte[] randomRead = randomResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            SormaUnitTest.assertCondition("2KB random binary survives TEXT round-trip",
                java.util.Arrays.equals(randomNoNull, randomRead));
            SormaUnitTest.assertCondition("2KB random binary length preserved", randomRead.length == 2048);

            // --- Repeating pattern binary ---
            byte[] pattern = new byte[1024];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) ((i % 255) + 1);
            }
            String patternStr = new String(pattern, StandardCharsets.ISO_8859_1);
            Person pPattern = new Person();
            pPattern.name = patternStr;
            pPattern.address = "repeating pattern";
            pPattern.social_number = 4;
            long rowIdPattern = orma.insertIntoPerson(pPattern);

            List<Person> patternResults = orma.selectFromPerson().idEq(rowIdPattern).toList();
            byte[] patternRead = patternResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            SormaUnitTest.assertCondition("Repeating pattern binary preserved",
                java.util.Arrays.equals(pattern, patternRead));

            // Verify pattern byte-by-byte
            boolean patternIntact = true;
            for (int i = 0; i < patternRead.length; i++) {
                if (patternRead[i] != (byte) ((i % 255) + 1)) {
                    patternIntact = false;
                    break;
                }
            }
            SormaUnitTest.assertCondition("Pattern sequence verified byte-by-byte", patternIntact);

            // --- Binary in multiple fields simultaneously ---
            byte[] nameBytes = new byte[512];
            byte[] addrBytes = new byte[512];
            rng.nextBytes(nameBytes);
            rng.nextBytes(addrBytes);
            for (int i = 0; i < nameBytes.length; i++) { if (nameBytes[i] == 0) nameBytes[i] = 1; }
            for (int i = 0; i < addrBytes.length; i++) { if (addrBytes[i] == 0) addrBytes[i] = 1; }

            Person pMulti = new Person();
            pMulti.name = new String(nameBytes, StandardCharsets.ISO_8859_1);
            pMulti.address = new String(addrBytes, StandardCharsets.ISO_8859_1);
            pMulti.social_number = 5;
            long rowIdMulti = orma.insertIntoPerson(pMulti);

            List<Person> multiResults = orma.selectFromPerson().idEq(rowIdMulti).toList();
            byte[] nameRead = multiResults.get(0).name.getBytes(StandardCharsets.ISO_8859_1);
            byte[] addrRead = multiResults.get(0).address.getBytes(StandardCharsets.ISO_8859_1);
            SormaUnitTest.assertCondition("Binary in name field preserved", java.util.Arrays.equals(nameBytes, nameRead));
            SormaUnitTest.assertCondition("Binary in address field preserved", java.util.Arrays.equals(addrBytes, addrRead));

            // Cleanup
            orma.deleteFromPerson().execute();

        } catch (Exception e) {
            SormaUnitTest.assertCondition("Raw bytes through TEXT test failed", false);
            e.printStackTrace();
        }
    }
}
