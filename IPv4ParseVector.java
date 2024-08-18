import jdk.incubator.vector.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class IPv4ParseVector {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final long ONE_GB = 1024 * 1024 * 1024;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.out.println("Usage: java UniqueIPCounter <file_path>");
            return;
        }

        String filePath = args[0];
        final File file = new File(filePath);
        final long length = file.length();
        try (var raf = new RandomAccessFile(file, "r")) {
            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            var worker = new ChunkProcessor(mappedFile.asSlice(0, length));
            worker.run();
        }

    }

    private record ChunkProcessor(MemorySegment chunk) {

        public void run() {
            for (var cursor = 0L; cursor < chunk.byteSize(); ) {
                var newlinePos = findByte(cursor, '\n');
                int ipInt = convertIPToBigEndian9(cursor, newlinePos);
                int ipIntNaive = convertIPToNaive(cursor, newlinePos);
                System.out.println(ipInt + " " + ipIntNaive + " " + (ipInt == ipIntNaive));
                if (ipInt != ipIntNaive) {
                    System.out.println(stringAt(cursor, newlinePos));
                }
                cursor = newlinePos + 1;
            }
        }

        private int convertIPToBigEndian9(long cursor, long newlinePos) {
            int shiftSize = 24;
            byte currentByte = 0;

            int result = 0;
            for (long i = cursor; i < newlinePos; i++) {
                byte c = chunk.get(JAVA_BYTE, i);
                if (c == '.') {
                    result |= (currentByte & 0xFF) << shiftSize;
                    shiftSize -= 8;
                    currentByte = 0;
                } else {
                    currentByte = (byte) (currentByte * (byte) 10 + (c - (byte) '0'));
                }
            }

            return result | (currentByte & 0xFF) << shiftSize;
        }


        private long findByte(long cursor, int b) {
            for (var i = cursor; i < chunk.byteSize(); i++) {
                if (chunk.get(JAVA_BYTE, i) == b) {
                    return i;
                }
            }
            throw new RuntimeException(((char) b) + " not found");
        }

        private String stringAt(long start, long limit) {
            return new String(chunk.asSlice(start, limit - start).toArray(JAVA_BYTE), StandardCharsets.UTF_8);
        }

        private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
        // private static final IntVector OFFSETS = IntVector.fromArray(SPECIES, new int[]{24, 16, 8, 0}, 0);

        private static final int[] max_size_lookup = new int[1229];

        static {
            max_size_lookup[175] = 1;
            max_size_lookup[291] = 2;
            max_size_lookup[571] = 3;
            max_size_lookup[320] = 2;
            max_size_lookup[600] = 2;
            max_size_lookup[104] = 3;
            max_size_lookup[670] = 3;
            max_size_lookup[174] = 3;
            max_size_lookup[206] = 3;
            max_size_lookup[344] = 2;
            max_size_lookup[576] = 2;
            max_size_lookup[112] = 3;
            max_size_lookup[646] = 2;
            max_size_lookup[182] = 2;
            max_size_lookup[214] = 3;
            max_size_lookup[314] = 3;
            max_size_lookup[346] = 3;
            max_size_lookup[410] = 3;
            max_size_lookup[695] = 3;
            max_size_lookup[135] = 3;
            max_size_lookup[231] = 3;
            max_size_lookup[267] = 3;
            max_size_lookup[363] = 3;
            max_size_lookup[427] = 3;
            max_size_lookup[627] = 3;
            max_size_lookup[691] = 3;
            max_size_lookup[819] = 3;
            max_size_lookup[350] = 2;
            max_size_lookup[582] = 2;
            max_size_lookup[118] = 3;
            max_size_lookup[640] = 2;
            max_size_lookup[176] = 2;
            max_size_lookup[208] = 3;
            max_size_lookup[316] = 3;
            max_size_lookup[348] = 3;
            max_size_lookup[412] = 3;
            max_size_lookup[689] = 2;
            max_size_lookup[129] = 2;
            max_size_lookup[225] = 3;
            max_size_lookup[269] = 2;
            max_size_lookup[365] = 2;
            max_size_lookup[429] = 3;
            max_size_lookup[629] = 3;
            max_size_lookup[693] = 3;
            max_size_lookup[821] = 3;
            max_size_lookup[366] = 3;
            max_size_lookup[270] = 3;
            max_size_lookup[462] = 3;
            max_size_lookup[534] = 3;
            max_size_lookup[726] = 3;
            max_size_lookup[854] = 3;
            max_size_lookup[230] = 3;
            max_size_lookup[358] = 3;
            max_size_lookup[614] = 3;
            max_size_lookup[701] = 3;
            max_size_lookup[141] = 3;
            max_size_lookup[237] = 3;
            max_size_lookup[257] = 3;
            max_size_lookup[353] = 3;
            max_size_lookup[417] = 3;
            max_size_lookup[633] = 3;
            max_size_lookup[697] = 3;
            max_size_lookup[825] = 3;
            max_size_lookup[354] = 3;
            max_size_lookup[258] = 3;
            max_size_lookup[450] = 3;
            max_size_lookup[538] = 3;
            max_size_lookup[730] = 3;
            max_size_lookup[858] = 3;
            max_size_lookup[234] = 3;
            max_size_lookup[362] = 3;
            max_size_lookup[618] = 3;
            max_size_lookup[732] = 3;
            max_size_lookup[540] = 3;
            max_size_lookup[924] = 3;
            max_size_lookup[44] = 3;
            max_size_lookup[428] = 3;
            max_size_lookup[684] = 3;
            max_size_lookup[460] = 3;
            max_size_lookup[716] = 3;
            max_size_lookup[1228] = 3;
        }


        private static final int[] pattern_lookup = new int[1229];

        static {
            pattern_lookup[44] = 17;
            pattern_lookup[104] = 63;
            pattern_lookup[112] = 57;
            pattern_lookup[118] = 55;
            pattern_lookup[129] = 31;
            pattern_lookup[135] = 33;
            pattern_lookup[141] = 29;
            pattern_lookup[174] = 45;
            pattern_lookup[175] = 0;
            pattern_lookup[176] = 37;
            pattern_lookup[182] = 39;
            pattern_lookup[206] = 72;
            pattern_lookup[208] = 64;
            pattern_lookup[214] = 66;
            pattern_lookup[225] = 58;
            pattern_lookup[230] = 25;
            pattern_lookup[231] = 60;
            pattern_lookup[234] = 23;
            pattern_lookup[237] = 56;
            pattern_lookup[257] = 11;
            pattern_lookup[258] = 32;
            pattern_lookup[267] = 15;
            pattern_lookup[269] = 13;
            pattern_lookup[270] = 34;
            pattern_lookup[291] = 27;
            pattern_lookup[314] = 21;
            pattern_lookup[316] = 19;
            pattern_lookup[320] = 9;
            pattern_lookup[344] = 3;
            pattern_lookup[346] = 48;
            pattern_lookup[348] = 46;
            pattern_lookup[350] = 1;
            pattern_lookup[353] = 38;
            pattern_lookup[354] = 5;
            pattern_lookup[358] = 52;
            pattern_lookup[362] = 50;
            pattern_lookup[363] = 42;
            pattern_lookup[365] = 40;
            pattern_lookup[366] = 7;
            pattern_lookup[410] = 75;
            pattern_lookup[412] = 73;
            pattern_lookup[417] = 65;
            pattern_lookup[427] = 69;
            pattern_lookup[428] = 44;
            pattern_lookup[429] = 67;
            pattern_lookup[450] = 59;
            pattern_lookup[460] = 26;
            pattern_lookup[462] = 61;
            pattern_lookup[534] = 16;
            pattern_lookup[538] = 14;
            pattern_lookup[540] = 35;
            pattern_lookup[571] = 54;
            pattern_lookup[576] = 30;
            pattern_lookup[582] = 28;
            pattern_lookup[600] = 36;
            pattern_lookup[614] = 79;
            pattern_lookup[618] = 77;
            pattern_lookup[627] = 24;
            pattern_lookup[629] = 22;
            pattern_lookup[633] = 20;
            pattern_lookup[640] = 10;
            pattern_lookup[646] = 12;
            pattern_lookup[670] = 18;
            pattern_lookup[684] = 71;
            pattern_lookup[689] = 4;
            pattern_lookup[691] = 51;
            pattern_lookup[693] = 49;
            pattern_lookup[695] = 6;
            pattern_lookup[697] = 47;
            pattern_lookup[701] = 2;
            pattern_lookup[716] = 53;
            pattern_lookup[726] = 43;
            pattern_lookup[730] = 41;
            pattern_lookup[732] = 8;
            pattern_lookup[819] = 78;
            pattern_lookup[821] = 76;
            pattern_lookup[825] = 74;
            pattern_lookup[854] = 70;
            pattern_lookup[858] = 68;
            pattern_lookup[924] = 62;
            pattern_lookup[1228] = 80;
        }

        static final int[][] patterns = {
                /* id: 0, hash: 000000aa */ {0, 2, 4, 6, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15},
                /* id: 1, hash: 00000154 */ {0, 15, 15, 15, 1, 3, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 84, 1, 2, 0},
                /* id: 2, hash: 000002a8 */ {1, 2, 15, 4, 15, 6, 15, 8, 0, 0, 15, 15, 15, 15, 15, 15, 15, 2, 3, 0},
                /* id: 3, hash: 00000152 */ {15, 2, 15, 15, 0, 3, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 82, 1, 2, 0},
                /* id: 4, hash: 000002a4 */ {0, 3, 15, 15, 1, 4, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0},
                /* id: 5, hash: 00000548 */ {1, 2, 4, 5, 15, 7, 15, 9, 0, 0, 15, 4, 15, 15, 15, 15, 72, 5, 3, 0},
                /* id: 6, hash: 000002a2 */ {15, 0, 3, 4, 15, 6, 15, 8, 15, 15, 2, 2, 15, 15, 15, 15, 15, 2, 3, 0},
                /* id: 7, hash: 00000544 */ {0, 1, 4, 5, 15, 7, 15, 9, 15, 0, 3, 3, 15, 15, 15, 15, 68, 5, 3, 0},
                /* id: 8, hash: 00000a88 */ {1, 2, 5, 6, 15, 8, 15, 10, 0, 0, 4, 4, 15, 15, 15, 15, 15, 10, 3, 0},
                /* id: 9, hash: 0000014a */ {15, 15, 4, 15, 0, 2, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 74, 1, 2, 0},
                /* id: 10, hash: 00000294 */ {0, 15, 5, 15, 1, 3, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0},
                /* id: 11, hash: 00000528 */ {1, 2, 15, 4, 6, 7, 15, 9, 0, 0, 15, 15, 15, 6, 15, 15, 40, 5, 3, 0},
                /* id: 12, hash: 00000292 */ {15, 2, 5, 15, 0, 3, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0},
                /* id: 13, hash: 00000524 */ {0, 3, 6, 15, 1, 4, 7, 9, 15, 15, 15, 15, 15, 15, 15, 15, 36, 5, 2, 0},
                /* id: 14, hash: 00000a48 */ {1, 2, 4, 5, 7, 8, 15, 10, 0, 0, 15, 4, 15, 7, 15, 15, 72, 10, 3, 0},
                /* id: 15, hash: 00000522 */ {15, 0, 3, 4, 6, 7, 15, 9, 15, 15, 2, 2, 15, 6, 15, 15, 34, 5, 3, 0},
                /* id: 16, hash: 00000a44 */ {0, 1, 4, 5, 7, 8, 15, 10, 15, 0, 3, 3, 15, 7, 15, 15, 68, 10, 3, 0},
                /* id: 17, hash: 00001488 */ {1, 2, 5, 6, 8, 9, 15, 11, 0, 0, 4, 4, 15, 8, 15, 15, 15, 20, 3, 0},
                /* id: 18, hash: 0000028a */ {15, 0, 15, 2, 5, 6, 15, 8, 15, 15, 15, 15, 4, 4, 15, 15, 15, 2, 3, 0},
                /* id: 19, hash: 00000514 */ {0, 1, 15, 3, 6, 7, 15, 9, 15, 0, 15, 15, 5, 5, 15, 15, 20, 5, 3, 0},
                /* id: 20, hash: 00000a28 */ {1, 2, 15, 4, 7, 8, 15, 10, 0, 0, 15, 15, 6, 6, 15, 15, 40, 10, 3, 0},
                /* id: 21, hash: 00000512 */ {15, 0, 2, 3, 6, 7, 15, 9, 15, 15, 15, 2, 5, 5, 15, 15, 18, 5, 3, 0},
                /* id: 22, hash: 00000a24 */ {0, 1, 3, 4, 7, 8, 15, 10, 15, 0, 15, 3, 6, 6, 15, 15, 36, 10, 3, 0},
                /* id: 23, hash: 00001448 */ {1, 2, 4, 5, 8, 9, 15, 11, 0, 0, 15, 4, 7, 7, 15, 15, 72, 20, 3, 0},
                /* id: 24, hash: 00000a22 */ {15, 0, 3, 4, 7, 8, 15, 10, 15, 15, 2, 2, 6, 6, 15, 15, 34, 10, 3, 0},
                /* id: 25, hash: 00001444 */ {0, 1, 4, 5, 8, 9, 15, 11, 15, 0, 3, 3, 7, 7, 15, 15, 68, 20, 3, 0},
                /* id: 26, hash: 00002888 */ {1, 2, 5, 6, 9, 10, 15, 12, 0, 0, 4, 4, 8, 8, 15, 15, 15, 40, 3, 0},
                /* id: 27, hash: 0000012a */ {15, 15, 15, 6, 0, 2, 4, 7, 15, 15, 15, 15, 15, 15, 15, 15, 42, 1, 2, 0},
                /* id: 28, hash: 00000254 */ {0, 15, 15, 7, 1, 3, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 84, 2, 2, 0},
                /* id: 29, hash: 000004a8 */ {1, 2, 15, 4, 15, 6, 8, 9, 0, 0, 15, 15, 15, 15, 15, 8, 15, 4, 3, 0},
                /* id: 30, hash: 00000252 */ {15, 2, 15, 7, 0, 3, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 82, 2, 2, 0},
                /* id: 31, hash: 000004a4 */ {0, 3, 15, 8, 1, 4, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0},
                /* id: 32, hash: 00000948 */ {1, 2, 4, 5, 15, 7, 9, 10, 0, 0, 15, 4, 15, 15, 15, 9, 72, 9, 3, 0},
                /* id: 33, hash: 000004a2 */ {15, 0, 3, 4, 15, 6, 8, 9, 15, 15, 2, 2, 15, 15, 15, 8, 15, 4, 3, 0},
                /* id: 34, hash: 00000944 */ {0, 1, 4, 5, 15, 7, 9, 10, 15, 0, 3, 3, 15, 15, 15, 9, 68, 9, 3, 0},
                /* id: 35, hash: 00001288 */ {1, 2, 5, 6, 15, 8, 10, 11, 0, 0, 4, 4, 15, 15, 15, 10, 15, 18, 3, 0},
                /* id: 36, hash: 0000024a */ {15, 15, 4, 7, 0, 2, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 74, 2, 2, 0},
                /* id: 37, hash: 00000494 */ {0, 15, 5, 8, 1, 3, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0},
                /* id: 38, hash: 00000928 */ {1, 2, 15, 4, 6, 7, 9, 10, 0, 0, 15, 15, 15, 6, 15, 9, 40, 9, 3, 0},
                /* id: 39, hash: 00000492 */ {15, 2, 5, 8, 0, 3, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0},
                /* id: 40, hash: 00000924 */ {0, 3, 6, 9, 1, 4, 7, 10, 15, 15, 15, 15, 15, 15, 15, 15, 36, 9, 2, 0},
                /* id: 41, hash: 00001248 */ {1, 2, 4, 5, 7, 8, 10, 11, 0, 0, 15, 4, 15, 7, 15, 10, 72, 18, 3, 0},
                /* id: 42, hash: 00000922 */ {15, 0, 3, 4, 6, 7, 9, 10, 15, 15, 2, 2, 15, 6, 15, 9, 34, 9, 3, 0},
                /* id: 43, hash: 00001244 */ {0, 1, 4, 5, 7, 8, 10, 11, 15, 0, 3, 3, 15, 7, 15, 10, 68, 18, 3, 0},
                /* id: 44, hash: 00002488 */ {1, 2, 5, 6, 8, 9, 11, 12, 0, 0, 4, 4, 15, 8, 15, 11, 15, 36, 3, 0},
                /* id: 45, hash: 0000048a */ {15, 0, 15, 2, 5, 6, 8, 9, 15, 15, 15, 15, 4, 4, 15, 8, 15, 4, 3, 0},
                /* id: 46, hash: 00000914 */ {0, 1, 15, 3, 6, 7, 9, 10, 15, 0, 15, 15, 5, 5, 15, 9, 20, 9, 3, 0},
                /* id: 47, hash: 00001228 */ {1, 2, 15, 4, 7, 8, 10, 11, 0, 0, 15, 15, 6, 6, 15, 10, 40, 18, 3, 0},
                /* id: 48, hash: 00000912 */ {15, 0, 2, 3, 6, 7, 9, 10, 15, 15, 15, 2, 5, 5, 15, 9, 18, 9, 3, 0},
                /* id: 49, hash: 00001224 */ {0, 1, 3, 4, 7, 8, 10, 11, 15, 0, 15, 3, 6, 6, 15, 10, 36, 18, 3, 0},
                /* id: 50, hash: 00002448 */ {1, 2, 4, 5, 8, 9, 11, 12, 0, 0, 15, 4, 7, 7, 15, 11, 72, 36, 3, 0},
                /* id: 51, hash: 00001222 */ {15, 0, 3, 4, 7, 8, 10, 11, 15, 15, 2, 2, 6, 6, 15, 10, 34, 18, 3, 0},
                /* id: 52, hash: 00002444 */ {0, 1, 4, 5, 8, 9, 11, 12, 15, 0, 3, 3, 7, 7, 15, 11, 68, 36, 3, 0},
                /* id: 53, hash: 00004888 */ {1, 2, 5, 6, 9, 10, 12, 13, 0, 0, 4, 4, 8, 8, 15, 12, 15, 72, 3, 0},
                /* id: 54, hash: 0000022a */ {15, 0, 15, 2, 15, 4, 7, 8, 15, 15, 15, 15, 15, 15, 6, 6, 42, 2, 3, 0},
                /* id: 55, hash: 00000454 */ {0, 1, 15, 3, 15, 5, 8, 9, 15, 0, 15, 15, 15, 15, 7, 7, 84, 4, 3, 0},
                /* id: 56, hash: 000008a8 */ {1, 2, 15, 4, 15, 6, 9, 10, 0, 0, 15, 15, 15, 15, 8, 8, 15, 8, 3, 0},
                /* id: 57, hash: 00000452 */ {15, 0, 2, 3, 15, 5, 8, 9, 15, 15, 15, 2, 15, 15, 7, 7, 82, 4, 3, 0},
                /* id: 58, hash: 000008a4 */ {0, 1, 3, 4, 15, 6, 9, 10, 15, 0, 15, 3, 15, 15, 8, 8, 15, 8, 3, 0},
                /* id: 59, hash: 00001148 */ {1, 2, 4, 5, 15, 7, 10, 11, 0, 0, 15, 4, 15, 15, 9, 9, 72, 17, 3, 0},
                /* id: 60, hash: 000008a2 */ {15, 0, 3, 4, 15, 6, 9, 10, 15, 15, 2, 2, 15, 15, 8, 8, 15, 8, 3, 0},
                /* id: 61, hash: 00001144 */ {0, 1, 4, 5, 15, 7, 10, 11, 15, 0, 3, 3, 15, 15, 9, 9, 68, 17, 3, 0},
                /* id: 62, hash: 00002288 */ {1, 2, 5, 6, 15, 8, 11, 12, 0, 0, 4, 4, 15, 15, 10, 10, 15, 34, 3, 0},
                /* id: 63, hash: 0000044a */ {15, 0, 15, 2, 4, 5, 8, 9, 15, 15, 15, 15, 15, 4, 7, 7, 74, 4, 3, 0},
                /* id: 64, hash: 00000894 */ {0, 1, 15, 3, 5, 6, 9, 10, 15, 0, 15, 15, 15, 5, 8, 8, 15, 8, 3, 0},
                /* id: 65, hash: 00001128 */ {1, 2, 15, 4, 6, 7, 10, 11, 0, 0, 15, 15, 15, 6, 9, 9, 40, 17, 3, 0},
                /* id: 66, hash: 00000892 */ {15, 0, 2, 3, 5, 6, 9, 10, 15, 15, 15, 2, 15, 5, 8, 8, 15, 8, 3, 0},
                /* id: 67, hash: 00001124 */ {0, 1, 3, 4, 6, 7, 10, 11, 15, 0, 15, 3, 15, 6, 9, 9, 36, 17, 3, 0},
                /* id: 68, hash: 00002248 */ {1, 2, 4, 5, 7, 8, 11, 12, 0, 0, 15, 4, 15, 7, 10, 10, 72, 34, 3, 0},
                /* id: 69, hash: 00001122 */ {15, 0, 3, 4, 6, 7, 10, 11, 15, 15, 2, 2, 15, 6, 9, 9, 34, 17, 3, 0},
                /* id: 70, hash: 00002244 */ {0, 1, 4, 5, 7, 8, 11, 12, 15, 0, 3, 3, 15, 7, 10, 10, 68, 34, 3, 0},
                /* id: 71, hash: 00004488 */ {1, 2, 5, 6, 8, 9, 12, 13, 0, 0, 4, 4, 15, 8, 11, 11, 15, 68, 3, 0},
                /* id: 72, hash: 0000088a */ {15, 0, 15, 2, 5, 6, 9, 10, 15, 15, 15, 15, 4, 4, 8, 8, 15, 8, 3, 0},
                /* id: 73, hash: 00001114 */ {0, 1, 15, 3, 6, 7, 10, 11, 15, 0, 15, 15, 5, 5, 9, 9, 20, 17, 3, 0},
                /* id: 74, hash: 00002228 */ {1, 2, 15, 4, 7, 8, 11, 12, 0, 0, 15, 15, 6, 6, 10, 10, 40, 34, 3, 0},
                /* id: 75, hash: 00001112 */ {15, 0, 2, 3, 6, 7, 10, 11, 15, 15, 15, 2, 5, 5, 9, 9, 18, 17, 3, 0},
                /* id: 76, hash: 00002224 */ {0, 1, 3, 4, 7, 8, 11, 12, 15, 0, 15, 3, 6, 6, 10, 10, 36, 34, 3, 0},
                /* id: 77, hash: 00004448 */ {1, 2, 4, 5, 8, 9, 12, 13, 0, 0, 15, 4, 7, 7, 11, 11, 72, 68, 3, 0},
                /* id: 78, hash: 00002222 */ {15, 0, 3, 4, 7, 8, 11, 12, 15, 15, 2, 2, 6, 6, 10, 10, 34, 34, 3, 0},
                /* id: 79, hash: 00004444 */ {0, 1, 4, 5, 8, 9, 12, 13, 15, 0, 3, 3, 7, 7, 11, 11, 68, 68, 3, 0},
                /* id: 80, hash: 00008888 */ {1, 2, 5, 6, 9, 10, 13, 14, 0, 0, 4, 4, 8, 8, 12, 12, 15, 15, 3, 0},
                /* id: 81, hash: 0000ffff */ {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 15, 15, 0, 0}};


        private int convertIPToIntVector(long cursor, long newlinePos) {
            long length = (newlinePos - cursor);
            var line = chunk.asSlice(cursor, length);

//            System.out.println(Arrays.toString(line.toArray(JAVA_BYTE)));

            VectorMask<Byte> mask = VectorMask.fromLong(SPECIES, (1L << (length)) - 1);
//                    masks[length - 7];

            ByteVector chars = ByteVector.fromMemorySegment(SPECIES, line, 0, ByteOrder.nativeOrder(), mask);
            ByteVector dots = ByteVector.broadcast(SPECIES, '.');

            VectorMask<Byte> dotMask = chars.eq(dots);
            chars = chars.sub((byte) '.', dotMask);

            VectorMask<Byte> numberMask = dotMask.not().and(mask);

            var numbers = chars.sub((byte) '0', numberMask); // TODO: try moving this in scalar

            long dotMaskLong = dotMask.toLong() | (1L << length);
            int hashcode = (int) ((dotMaskLong >> 5) ^ (dotMaskLong & 0x03ff));

            int maxLength = max_size_lookup[hashcode];
            int index = pattern_lookup[hashcode];
            var pattern = patterns[index];
            System.out.println(index);
            var shuffle = VectorShuffle.fromArray(SPECIES, pattern, 0);
            switch (maxLength) {
                case 1:
                    return numbers.rearrange(shuffle).reinterpretAsInts().lane(0);
                case 2:
                    var rearranged = numbers.rearrange(shuffle);
                    System.out.println(Arrays.toString(rearranged.toArray()));
                    var lowerLong = rearranged.reinterpretAsLongs().lane(0);
                    var w1 = lowerLong >> 32;
                    var w2 = lowerLong & 0xffffffffL;
                    return (int) (10 * w2 + w1);
                case 3:
                    System.out.println(Arrays.toString(numbers.toArray()));
                    var rearranged2 = numbers.rearrange(shuffle);
                    System.out.println(Arrays.toString(rearranged2.toArray()));
                    var weights = ByteVector.fromArray(SPECIES, new byte[]{10, 1, 10, 1, 10, 1, 10, 1, 100, 0, 100, 0, 100, 0, 100},
                            0, VectorMask.fromLong(SPECIES, 0x7FFFL));
                    var weighted = rearranged2.mul(weights);
                    var m = VectorMask.fromArray(SPECIES, new boolean[]{true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, false,}, 0);
                    System.out.println(Arrays.toString(weighted.toArray()));
                    var shift1 = weighted.rearrange(VectorShuffle.fromArray(SPECIES, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15}, 0));
                    System.out.println(Arrays.toString(shift1.toArray()));
                    var partialSum = weighted.add(shift1, m);
                    partialSum = partialSum.mul(m.toVector().abs());
                    var shift8 = weighted.rearrange(VectorShuffle.fromArray(SPECIES, new int[]{8, 9, 10, 11, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,}, 0));
                    var aligned = shift8.add(partialSum);
                    var res = aligned.rearrange(VectorShuffle.fromArray(SPECIES, new int[]{0, 2, 4, 6, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,}, 0));
                    return res.reinterpretAsInts().lane(0);
            }

            System.out.println(hashcode);

//
//            System.out.println(Arrays.toString(numberMask.toArray()));
            System.out.println(Arrays.toString(chars.toArray()));

            return chars.reduceLanes(VectorOperators.OR);
        }

        private int convertIPToNaive(long cursor, long newlinePos) {
            var ip = stringAt(cursor, newlinePos);
            String[] parts = ip.split("\\.");
            int ipInt = 0;

            for (String part : parts) {
                int value = Integer.parseInt(part);
                ipInt = (ipInt << 8) | value;
            }

            return ipInt;
        }

        int little2big(int i) {
            return (i & 0xff) << 24 | (i & 0xff00) << 8 | (i & 0xff0000) >> 8 | (i >> 24) & 0xff;
        }
    }
}
