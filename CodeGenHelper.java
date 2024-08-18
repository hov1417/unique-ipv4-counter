import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

public class CodeGenHelper {

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            System.out.println(1 << i);
        }
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

    static void printAllPatternsByHash() {
        Map<Long, List<String>> groupedPatterns = new TreeMap<>();
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                for (int k = 1; k <= 3; k++) {
                    for (int t = 1; t <= 3; t++) {
                        groupedPatterns.computeIfAbsent(
                                patternHashCode(sample(i, j, k, t)),
                                integer -> new ArrayList<>()
                        ).add(key(i, j, k, t));
                    }
                }
            }
        }
        groupedPatterns.forEach((key, value) -> {
            System.out.println(key + "-" + String.join(" ", value));
        });
    }

    static void printAllHashAndMaxLength() {
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                for (int k = 1; k <= 3; k++) {
                    for (int t = 1; t <= 3; t++) {
                        System.out.println(patternHashCode(sample(i, j, k, t)) + " " + Math.max(Math.max(i, j), Math.max(k, t)));
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

    private static void test() {
        ByteVector chars = ByteVector.fromArray(ByteVector.SPECIES_64, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, 0);
        var second = ByteVector.fromArray(ByteVector.SPECIES_64, new byte[]{0, 0, 0, 0, 0, 0, 0, 0}, 0);
        chars = chars.lanewise(VectorOperators.COMPRESS_BITS, second);
        System.out.println(Arrays.toString(chars.toArray()));
    }


    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;

    static private long patternHashCode(String sample) {
        VectorMask<Byte> mask = VectorMask.fromLong(SPECIES, (1L << (sample.length())) - 1);
//        VectorMask.fromLong()

        ByteVector chars = ByteVector.fromArray(SPECIES, sample.getBytes(), 0, mask);

        ByteVector dots = ByteVector.broadcast(SPECIES, '.');

        VectorMask<Byte> dotMask = chars.eq(dots);

        long dotMaskLong = dotMask.toLong() | (1L << (sample.length()));
        return (dotMaskLong >> 5) ^ (dotMaskLong & 0x03ff);
    }

}
