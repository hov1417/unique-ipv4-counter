//import jdk.incubator.vector.ByteVector;
//import jdk.incubator.vector.VectorMask;
//import jdk.incubator.vector.VectorOperators;
//import jdk.incubator.vector.VectorSpecies;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class CodeGenHelper {

    public static void main(String[] args) {
//        var sample = sample(2, 1, 1, 1);
//        long[] l = reinterpretByteArrayToLongArray(("12.2.3.4" + "\n").getBytes());
//        var x1 = num(l[0], l.length == 1 ? 0 : l[1]);
//        var x2 = num2(l[0], l.length == 1 ? 0 : l[1]);
//        System.out.println(x1 + " " + x2 + " " + (x1 == x2));

        generateCode();
    }

    record Code(int count, String line) {
    }

    public static void generateCode() {
        var lines = new ArrayList<Code>();
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                for (int k = 1; k <= 3; k++) {
                    for (int t = 1; t <= 3; t++) {
                        if (i == 1 && j == 1 && k == 1 && t == 1) {
                            continue;
                        }
                        var sample = sample(i, j, k, t);
                        long[] l = reinterpretByteArrayToLongArray((sample + "\n").getBytes());
                        var hashcode = (int) getHashcode(l[0], l.length == 1 ? 0 : l[1]);

                        var line = "else if (hashcode == " + hashcode + ") {return (int) (";

                        line += "((" + byteParse(0, i) + ") << 24) | ";
                        line += "((" + byteParse(i + 1, j) + ") << 16) | ";
                        line += "((" + byteParse(i + 1 + j + 1, k) + ") << 8) | ";
                        line += "((" + byteParse(i + 1 + j + 1 + k + 1, t) + "))";

                        line += ");}";

                        lines.add(new Code(numberOfCases(i, j, k, t), line));
                    }
                }
            }
        }
        lines.stream().sorted((a, b) -> b.count - a.count).map(Code::line).forEach(System.out::println);
    }

    static final int num2(long value1, long value2) {
        value1 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        value2 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        int hashcode = (int) getHashcode(value1, value2);
        System.out.println(hashcode);
        return switch (hashcode) {
            case -2147434464 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | ((((value1 >>> 32) & 0xFF)) << 8) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF))));
            case -2147426304 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | ((((value1 >>> 32) & 0xFF)) << 8) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
            case -2143256544 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
            case -2143248384 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
            case -2141159424 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case -1073700864 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case -1071611904 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case -536838144 ->
                    (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 4227168 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
            case 4235328 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
            case 6324288 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1073782848 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case 1075871808 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1610645568 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 2130016 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case 536903776 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 32880 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 1073799168 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case 1075888128 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1610661888 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 2146336 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case 536920096 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 49200 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 536928256 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case 57360 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 61440 ->
                    (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case 12583008 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
            case 12591168 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
            case 14680128 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1082138688 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case 1084227648 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1619001408 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 10485856 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case 545259616 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 8388720 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 1082155008 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case 1084243968 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case 1619017728 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 10502176 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case 545275936 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 8405040 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 545284096 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case 8413200 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 8417280 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case 14680096 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case 549453856 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case 12582960 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 549462016 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case 12591120 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case 12595200 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case 14680080 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
            case 14684160 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case 15728640 ->
                    (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
            case -1073717248 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
            case -1071628288 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
            case -536854528 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case -2145370080 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case -1610596320 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case -2147467216 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case -1610588160 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case -2147459056 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case -2147454976 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case -2141192160 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
            case -1606418400 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
            case -2143289296 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case -1606410240 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case -2143281136 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case -2143277056 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case -2141192176 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
            case -2141188096 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case -2140143616 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
            case -536862720 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
            case -1073733616 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
            case -1073729536 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case -1071644656 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
            case -1071640576 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
            case -1070596096 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
            case -536866816 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | ((((value2 >>> 32) & 0xFF))));
            case -535822336 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | (((((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
            case -268435456 ->
                    (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | (((((value2 >>> 32) & 0xFF) * 100) + (((value2 >>> 40) & 0xFF) * 10) + ((value2 >>> 48) & 0xFF))));

            default -> throw new IllegalStateException("Unexpected value: " + hashcode);
        };
    }

    static final int num(long value1, long value2) {
        value1 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        value2 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        int shiftSize = 24;
        byte currentByte = 0;

        int result = 0;
        for (int i = 0; i < 8; i++) {
            byte c = (byte) (value1 & 0xFFL);
            value1 >>>= 8;
            if (c == 0x0e) {
                result |= (currentByte & 0xFF) << shiftSize;
                shiftSize -= 8;
                currentByte = 0;
            } else {
                currentByte = (byte) (currentByte * 10 + c);
            }
        }
        var endIndex = Long.numberOfTrailingZeros(getLFCode(value2)) >> 3;
        for (int i = 0; i < endIndex; i++) {
            byte c = (byte) (value2 & 0xFFL);
            value2 >>>= 8;
            if (c == 0x0e) {
                result |= (currentByte & 0xFF) << shiftSize;
                shiftSize -= 8;
                currentByte = 0;
            } else {
                currentByte = (byte) (currentByte * 10 + c);
            }
        }

        return result | (currentByte & 0xFF);
    }

    static String byteAccess(int i) {
        String value;
        if (i < 8) {
            value = "value1";
        } else {
            value = "value2";
        }
        String shift;
        int shiftSize = i < 8 ? (i * 8) : ((i - 8) * 8);
        if (shiftSize != 0) {
            shift = "(" + value + ">>> " + shiftSize + ")";
        } else {
            shift = value;
        }
        return "(" + shift + " & 0xFF )";
    }

    static String byteParse(int offset, int length) {
        return switch (length) {
            case 1 -> byteAccess(offset);
            case 2 -> "(" + byteAccess(offset) + " * 10 ) + " + byteAccess(offset + 1);
            case 3 ->
                    "(" + byteAccess(offset) + " * 100) + (" + byteAccess(offset + 1) + " * 10) + " + byteAccess(offset + 2);
            default -> throw new IllegalStateException("Unexpected value: " + length);
        };
    }

    public static long[] reinterpretByteArrayToLongArray(byte[] byteArray) {
        // Calculate the number of longs needed (8 bytes per long)
        int longCount = Math.ceilDiv(byteArray.length, Long.BYTES);

        // Create a padded byte array with the size being a multiple of 8
        byte[] paddedByteArray = Arrays.copyOf(byteArray, longCount * Long.BYTES);

        // Create a long array to hold the values
        long[] longArray = new long[longCount];
        // Wrap the padded byte array in a ByteBuffer for easy manipulation
        ByteBuffer buffer = ByteBuffer.wrap(paddedByteArray);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        longArray[0] = buffer.getLong(0);
        if (longCount == 1) {
            return longArray;
        }
        longArray[1] = buffer.getLong(8);// >>> ((8 - byteArray.length) * 8);

        return longArray;
    }

    public static void hashSearch() {
        long[] numbers = new long[]{10506272L,
                1075888144L,
                1081456L,
                1083187280L,
                1084248064L,
                1097776L,
                1105936L,
                12595200L,
                1344307280L,
                1350590480L,
                1352667200L,
                13639760L,
                14684160L,
                14684192L,
                15728640L,
                1610649664L,
                1610665984L,
                1611702336L,
                1620049984L,
                1620066304L,
                1879105536L,
                2146352L,
                2147500080L,
                2147508240L,
                2147512320L,
                2150645792L,
                2150678576L,
                2151678000L,
                2151686160L,
                2151690240L,
                2153775120L,
                2153779200L,
                2154823680L,
                2154823712L,
                2154856464L,
                2422243376L,
                2684416000L,
                2688593920L,
                270565488L,
                274759760L,
                274763872L,
                276824176L,
                276840496L,
                276848656L,
                278925408L,
                281018416L,
                281026576L,
                283115536L,
                283119680L,
                2952806432L,
                2952814592L,
                2956984352L,
                2956992512L,
                3221233680L,
                3221237760L,
                3223322640L,
                3223326720L,
                3224371200L,
                3224387584L,
                3224403984L,
                3489689600L,
                3758100480L,
                3758133248L,
                3758141440L,
                3759144960L,
                4026531840L,
                4026540032L,
                4026548224L,
                536907872L,
                536924192L,
                536932352L,
                542154816L,
                546308192L,
                546324512L,
                546332672L,
                550502432L,
                550510592L,
                61440L,
                817893472L,
                8417280};
//        for(int x = 2; x < 1000; x++) {
        HashSet<Long> items = new HashSet<>();
        for (long number : numbers) {
//                number = (number >>> 15) + (number & 0xFFF_FFL);
            items.add((number) % 263 + (number) % 3);
        }
        System.out.println(Arrays.toString(items.toArray()));
        System.out.println(items.size());
//        if (items.size() == numbers.length) {
//            System.out.println(x);
//        }
//        }
    }

    static void printAllPatterns() {
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                for (int k = 1; k <= 3; k++) {
                    for (int t = 1; t <= 3; t++) {
                        System.out.println(key(i, j, k, t) + " " + sample(i, j, k, t));
                    }
                }
            }
        }
    }

//    static void printAllPatternsByHash() {
//        Map<Long, List<String>> groupedPatterns = new TreeMap<>();
//        for (int i = 1; i <= 3; i++) {
//            for (int j = 1; j <= 3; j++) {
//                for (int k = 1; k <= 3; k++) {
//                    for (int t = 1; t <= 3; t++) {
//                        groupedPatterns.computeIfAbsent(
//                                patternHashCode(sample(i, j, k, t)),
//                                integer -> new ArrayList<>()
//                        ).add(key(i, j, k, t));
//                    }
//                }
//            }
//        }
//        groupedPatterns.forEach((key, value) -> {
//            System.out.println(key + "-" + String.join(" ", value));
//        });
//    }

    static void printAllHashAndMaxLength() {
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                for (int k = 1; k <= 3; k++) {
                    for (int t = 1; t <= 3; t++) {
                        System.out.println(sample(i, j, k, t));
                    }
                }
            }
        }
    }

    private static String key(int i, int j, int k, int t) {
        return "(" + i + " " + j + " " + k + " " + t + ")";
    }

    private static String sample(int i, int j, int k, int t) {
        return "2".repeat(i) + "."
                + "2".repeat(j) + "."
                + "2".repeat(k) + "."
                + "2".repeat(t);
    }

    private static int numberOfCases(int i, int j, int k, int t) {
        return numberOfCases(i) * numberOfCases(j) * numberOfCases(k) * numberOfCases(t);
    }

    private static int numberOfCases(int i) {
        return switch (i) {
            case 1 -> 10;
            case 2 -> 89;
            case 3 -> 256 - 10 - 89;
            default -> 0;
        };
    }

    private static long getHashcode(long value1, long value2) {
        value1 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        value2 &= 0x0F_0F_0F_0F_0F_0F_0F_0FL;

        long x1 = getDotCode(value1);
        long x2 = getDotCode(value2) | getLFCode(value2);
//            System.out.println(Long.toHexString(x1));
//            System.out.println(Long.toHexString(x2));

//            int hashcode = (int) (x2 | ((x1 >>> 40) ^ (x1 & 0x03_ff_ff_ff_ffL)));
        long hashcode = (x2 >>> 2) | x1;
        hashcode = (long) ((hashcode >>> 33) ^ (hashcode & 0xff_ff_ff_ffL));

//        hashcode = (hashcode >>> 33) | (hashcode & 0xFF_FF_FF_FFL);
//        hashcode = (hashcode >>> 15) + (hashcode & 0xFFF_FFL);
//        hashcode = (int) ((hashcode >> 5) ^ (hashcode & 0x03ff));
        return hashcode;
//        return (hashcode % 37 + (hashcode / 37) + (hashcode / (17 * 17))) % 137;
    }

    static final long getDotCode(final long w) {
        long x = w ^ 0x0E0E0E0E0E0E0E0EL; // xor with ........
        return (x - 0x0101010101010101L) & (~x & 0x8080808080808080L);
    }

    // Get new line pos code
    static final long getLFCode(final long w) {
        long x = w ^ 0x0A0A0A0A0A0A0A0AL; // xor with \n\n\n\n\n\n\n\n
        return (x - 0x0101010101010101L) & (~x & 0x8080808080808080L);
    }


//    private static void test() {
//        ByteVector chars = ByteVector.fromArray(ByteVector.SPECIES_64, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, 0);
//        var second = ByteVector.fromArray(ByteVector.SPECIES_64, new byte[]{0, 0, 0, 0, 0, 0, 0, 0}, 0);
//        chars = chars.lanewise(VectorOperators.COMPRESS_BITS, second);
//        System.out.println(Arrays.toString(chars.toArray()));
//    }
//
//
//    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
//
//    static private long patternHashCode(String sample) {
//        VectorMask<Byte> mask = VectorMask.fromLong(SPECIES, (1L << (sample.length())) - 1);
////        VectorMask.fromLong()
//
//        ByteVector chars = ByteVector.fromArray(SPECIES, sample.getBytes(), 0, mask);
//
//        ByteVector dots = ByteVector.broadcast(SPECIES, '.');
//
//        VectorMask<Byte> dotMask = chars.eq(dots);
//
//        long dotMaskLong = dotMask.toLong() | (1L << (sample.length()));
//        return (dotMaskLong >> 5) ^ (dotMaskLong & 0x03ff);
//    }

}
