import jdk.incubator.vector.*;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLongArray;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class UniqueIPCounter {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final long ONE_GB = 1024 * 1024 * 1024;
    private static final Unsafe UNSAFE = unsafe();

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.out.println("Usage: java UniqueIPCounter <file_path>");
            return;
        }

        String filePath = args[0];
        final var uniqueIPs = new MaxBitSet();
        final File file = new File(filePath);
        final long length = file.length();
        final long chunkSize =
                Math.min(
                        args.length > 1 ? Long.parseLong(args[1]) : 2 * ONE_GB,
                        Math.ceilDiv(length, NUM_THREADS)
                );
//        System.out.println("size " + length);
        if (length < 1024 * 1024) {
            System.out.println("using a single thread");
            unchunkedExecute(length, file, uniqueIPs);
        } else {
            parallelExecute(chunkSize, length, file, uniqueIPs);
        }

        System.out.println("Number of unique IP addresses: " + uniqueIPs.count());
    }

    private static void unchunkedExecute(
            long length,
            File file,
            MaxBitSet uniqueIPs
    ) throws IOException {
        try (var raf = new RandomAccessFile(file, "r")) {
            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            var worker = new ChunkProcessor(mappedFile.asSlice(0, length), uniqueIPs);
            worker.run();
        }
    }

    private static void parallelExecute(
            long chunkSize,
            long length,
            File file,
            MaxBitSet uniqueIPs
    ) throws IOException, InterruptedException {
        final var chunkStartOffsets = new long[(int) Math.ceilDiv(length, chunkSize)];
        try (var raf = new RandomAccessFile(file, "r")) {
            for (int i = 1; i < chunkStartOffsets.length; i++) {
                var start = chunkStartOffsets[i - 1] + chunkSize;
                raf.seek(start);
                while (true) {
                    var nextByte = raf.read();
                    if (nextByte == (byte) '\n' || nextByte == -1) {
                        break;
                    }
                }
                start = raf.getFilePointer();
                chunkStartOffsets[i] = start;
            }

            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            printStats();
            try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS,
                    new RegionProcessorThreadFactory())) {
                for (int i = 0; i < chunkStartOffsets.length; i++) {
                    final long chunkStart = chunkStartOffsets[i];
                    final long chunkLimit = (i + 1 < chunkStartOffsets.length) ? chunkStartOffsets[i + 1] : length;
                    executor.execute(new ChunkProcessor(
                            mappedFile.asSlice(chunkStart, chunkLimit - chunkStart), uniqueIPs));
                }
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.HOURS);
            }
        }
    }

    private static void printStats() {
        // Get current size of heap in bytes.
        long heapSize = Runtime.getRuntime().totalMemory();

        // Get maximum size of heap in bytes. The heap cannot grow beyond this size.
        // Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory();

        // Get amount of free memory within the heap in bytes. This size will
        // increase after garbage collection and decrease as new objects are created.
        long heapFreeSize = Runtime.getRuntime().freeMemory();

        System.out.println("heap size: " + heapSize / 1024 / 1024
                + "MB; max size: " + heapMaxSize / 1024 / 1024
                + "MB; free: " + heapFreeSize / 1024 / 1024 + "MB;");
    }

    private static class RegionProcessorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        }

    }

    private static class ForkRegionProcessorThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);

            worker.setDaemon(true);
            worker.setPriority(Thread.MAX_PRIORITY);

            return worker;
        }
    }

    private static class ChunkProcessor implements Runnable {
        private final MemorySegment chunk;
        private final long base;
        private final MaxBitSet uniqueIPs;

        ChunkProcessor(MemorySegment chunk, MaxBitSet uniqueIPs) {
            this.chunk = chunk;
            this.base = chunk.address();
            this.uniqueIPs = uniqueIPs;
        }

        @Override
        public void run() {
            for (var cursor = 0L; cursor < chunk.byteSize(); ) {
//                printStats();
                var newlinePos = findByte(
                        cursor,
                        '\n');
                int ipInt = convertIPToBigEndian9(cursor, newlinePos);
                uniqueIPs.set(ipInt);

                cursor = newlinePos + 1;
            }
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
            return new String(
                    chunk.asSlice(start, limit - start).toArray(JAVA_BYTE),
                    StandardCharsets.UTF_8
            );
        }

        private int convertIPToBigEndian(long cursor, long newlinePos) {
            var ip = stringAt(cursor, newlinePos);
            String[] parts = ip.split("\\.");
            int ipInt = 0;

            for (String part : parts) {
                int value = Integer.parseInt(part);
                ipInt = (ipInt << 8) | value;
            }

            return ipInt;
        }

        private int convertIPToBigEndian2(long cursor, long newlinePos) {
            var ip = stringAt(cursor, newlinePos);
            int ipInt = 0;
            int octet = 0;
            for (final char c : ip.toCharArray()) {
                if (c == '.') {
                    ipInt = (ipInt << 8) | octet;
                    octet = 0;
                } else {
                    octet *= 10;
                    octet += c - '0';
                }
            }

            ipInt = (ipInt << 8) | octet;

            return ipInt;
        }

        private int convertIPToBigEndian3(long cursor, long newlinePos) {
            int ipInt = 0;
            int octet = 0;
            while (cursor < newlinePos) {
                var c = chunk.get(JAVA_BYTE, cursor);
                if (c == '.') {
                    ipInt = (ipInt << 8) | octet;
                    octet = 0;
                } else {
                    octet *= 10;
                    octet += c - '0';
                }
                cursor++;
            }

            ipInt = (ipInt << 8) | octet;

            return ipInt;
        }

        private int convertIPToBigEndian4(long cursor, long newlinePos) {
            var parseRes = parseNumber(cursor, newlinePos);
            int ipInt = parseRes.num << 24;
            parseRes = parseNumber(parseRes.cursor, newlinePos);
            ipInt |= parseRes.num << 16;
            parseRes = parseNumber(parseRes.cursor, newlinePos);
            ipInt |= parseRes.num << 8;
            parseRes = parseNumber(parseRes.cursor, newlinePos);
            ipInt |= parseRes.num;

            return ipInt;
        }

//        private int convertIPToBigEndian5(long cursor, long newlinePos) {
//            int ipInt = 0;
//            int octet = 0;
//            while (cursor < newlinePos) {
//                // wrong
//                var c = UNSAFE.getChar(base + cursor);
//                if (c == '.') {
//                    ipInt = (ipInt << 8) | octet;
//                    octet = 0;
//                } else {
//                    octet *= 10;
//                    octet += c - '0';
//                }
//                cursor++;
//            }
//
//            ipInt = (ipInt << 8) | octet;
//
//            return ipInt;
//        }

        private static final VectorSpecies<Integer> SPECIES_I = IntVector.SPECIES_128;
        private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
        private static final IntVector OFFSETS = IntVector.fromArray(SPECIES_I, new int[]{24, 16, 8, 0}, 0);

        private int convertIPToBigEndian6(long cursor, long newlinePos) {
            String ip = stringAt(cursor, newlinePos);

            int[] bytes = new int[4];
            int byteIndex = 0;
            int currentByte = 0;

            for (int i = 0; i < ip.length(); i++) {
                char c = ip.charAt(i);
                if (c == '.') {
                    bytes[byteIndex++] = currentByte;
                    currentByte = 0;
                } else {
                    currentByte = currentByte * 10 + (c - '0');
                }
            }
            bytes[byteIndex] = currentByte; // Store the last byte

            IntVector vector = IntVector.fromArray(SPECIES_I, bytes, 0).lanewise(VectorOperators.LSHL, OFFSETS);

            return vector.reduceLanes(VectorOperators.OR);
        }

        private int convertIPToBigEndian7(long cursor, long newlinePos) {
            int[] bytes = new int[4];
            int byteIndex = 0;
            int currentByte = 0;

            for (long i = cursor; i < newlinePos; i++) {
                byte c = chunk.get(JAVA_BYTE, i);
                if (c == '.') {
                    bytes[byteIndex++] = currentByte;
                    currentByte = 0;
                } else {
                    currentByte = currentByte * 10 + (c - '0');
                }
            }
            bytes[byteIndex] = currentByte; // Store the last byte

            IntVector vector = IntVector.fromArray(SPECIES_I, bytes, 0).lanewise(VectorOperators.LSHL, OFFSETS);

            return vector.reduceLanes(VectorOperators.OR);
        }

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


        private static final int[][] pattern_lookup = new int[1229][];

        static {
            /*00*/
            pattern_lookup[175] = /* id: 0, hash: 000000aa */new int[]{0, 2, 4, 6, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15};
            /*01*/
            pattern_lookup[350] = /* id: 1, hash: 00000154 */new int[]{0, 15, 15, 15, 1, 3, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 84, 1, 2, 0};
            /*02*/
            pattern_lookup[701] = /* id: 2, hash: 000002a8 */new int[]{1, 2, 15, 4, 15, 6, 15, 8, 0, 0, 15, 15, 15, 15, 15, 15, 15, 2, 3, 0};
            /*03*/
            pattern_lookup[344] = /* id: 3, hash: 00000152 */new int[]{15, 2, 15, 15, 0, 3, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 82, 1, 2, 0};
            /*04*/
            pattern_lookup[689] = /* id: 4, hash: 000002a4 */new int[]{0, 3, 15, 15, 1, 4, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0};
            /*05*/
            pattern_lookup[354] = /* id: 5, hash: 00000548 */new int[]{1, 2, 4, 5, 15, 7, 15, 9, 0, 0, 15, 4, 15, 15, 15, 15, 72, 5, 3, 0};
            /*06*/
            pattern_lookup[695] = /* id: 6, hash: 000002a2 */new int[]{15, 0, 3, 4, 15, 6, 15, 8, 15, 15, 2, 2, 15, 15, 15, 15, 15, 2, 3, 0};
            /*07*/
            pattern_lookup[366] = /* id: 7, hash: 00000544 */new int[]{0, 1, 4, 5, 15, 7, 15, 9, 15, 0, 3, 3, 15, 15, 15, 15, 68, 5, 3, 0};
            /*08*/
            pattern_lookup[732] = /* id: 8, hash: 00000a88 */new int[]{1, 2, 5, 6, 15, 8, 15, 10, 0, 0, 4, 4, 15, 15, 15, 15, 15, 10, 3, 0};
            /*09*/
            pattern_lookup[320] = /* id: 9, hash: 0000014a */new int[]{15, 15, 4, 15, 0, 2, 5, 7, 15, 15, 15, 15, 15, 15, 15, 15, 74, 1, 2, 0};
            /*10*/
            pattern_lookup[640] = /* id: 10, hash: 00000294 */new int[]{0, 15, 5, 15, 1, 3, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0};
            /*11*/
            pattern_lookup[257] = /* id: 11, hash: 00000528 */new int[]{1, 2, 15, 4, 6, 7, 15, 9, 0, 0, 15, 15, 15, 6, 15, 15, 40, 5, 3, 0};
            /*12*/
            pattern_lookup[646] = /* id: 12, hash: 00000292 */new int[]{15, 2, 5, 15, 0, 3, 6, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 2, 0};
            /*13*/
            pattern_lookup[269] = /* id: 13, hash: 00000524 */new int[]{0, 3, 6, 15, 1, 4, 7, 9, 15, 15, 15, 15, 15, 15, 15, 15, 36, 5, 2, 0};
            /*14*/
            pattern_lookup[538] = /* id: 14, hash: 00000a48 */new int[]{1, 2, 4, 5, 7, 8, 15, 10, 0, 0, 15, 4, 15, 7, 15, 15, 72, 10, 3, 0};
            /*15*/
            pattern_lookup[267] = /* id: 15, hash: 00000522 */new int[]{15, 0, 3, 4, 6, 7, 15, 9, 15, 15, 2, 2, 15, 6, 15, 15, 34, 5, 3, 0};
            /*16*/
            pattern_lookup[534] = /* id: 16, hash: 00000a44 */new int[]{0, 1, 4, 5, 7, 8, 15, 10, 15, 0, 3, 3, 15, 7, 15, 15, 68, 10, 3, 0};
            /*17*/
            pattern_lookup[44] = /* id: 17, hash: 00001488 */new int[]{1, 2, 5, 6, 8, 9, 15, 11, 0, 0, 4, 4, 15, 8, 15, 15, 15, 20, 3, 0};
            /*18*/
            pattern_lookup[670] = /* id: 18, hash: 0000028a */new int[]{15, 0, 15, 2, 5, 6, 15, 8, 15, 15, 15, 15, 4, 4, 15, 15, 15, 2, 3, 0};
            /*19*/
            pattern_lookup[316] = /* id: 19, hash: 00000514 */new int[]{0, 1, 15, 3, 6, 7, 15, 9, 15, 0, 15, 15, 5, 5, 15, 15, 20, 5, 3, 0};
            /*20*/
            pattern_lookup[633] = /* id: 20, hash: 00000a28 */new int[]{1, 2, 15, 4, 7, 8, 15, 10, 0, 0, 15, 15, 6, 6, 15, 15, 40, 10, 3, 0};
            /*21*/
            pattern_lookup[314] = /* id: 21, hash: 00000512 */new int[]{15, 0, 2, 3, 6, 7, 15, 9, 15, 15, 15, 2, 5, 5, 15, 15, 18, 5, 3, 0};
            /*22*/
            pattern_lookup[629] = /* id: 22, hash: 00000a24 */new int[]{0, 1, 3, 4, 7, 8, 15, 10, 15, 0, 15, 3, 6, 6, 15, 15, 36, 10, 3, 0};
            /*23*/
            pattern_lookup[234] = /* id: 23, hash: 00001448 */new int[]{1, 2, 4, 5, 8, 9, 15, 11, 0, 0, 15, 4, 7, 7, 15, 15, 72, 20, 3, 0};
            /*24*/
            pattern_lookup[627] = /* id: 24, hash: 00000a22 */new int[]{15, 0, 3, 4, 7, 8, 15, 10, 15, 15, 2, 2, 6, 6, 15, 15, 34, 10, 3, 0};
            /*25*/
            pattern_lookup[230] = /* id: 25, hash: 00001444 */new int[]{0, 1, 4, 5, 8, 9, 15, 11, 15, 0, 3, 3, 7, 7, 15, 15, 68, 20, 3, 0};
            /*26*/
            pattern_lookup[460] = /* id: 26, hash: 00002888 */new int[]{1, 2, 5, 6, 9, 10, 15, 12, 0, 0, 4, 4, 8, 8, 15, 15, 15, 40, 3, 0};
            /*27*/
            pattern_lookup[291] = /* id: 27, hash: 0000012a */new int[]{15, 15, 15, 6, 0, 2, 4, 7, 15, 15, 15, 15, 15, 15, 15, 15, 42, 1, 2, 0};
            /*28*/
            pattern_lookup[582] = /* id: 28, hash: 00000254 */new int[]{0, 15, 15, 7, 1, 3, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 84, 2, 2, 0};
            /*29*/
            pattern_lookup[141] = /* id: 29, hash: 000004a8 */new int[]{1, 2, 15, 4, 15, 6, 8, 9, 0, 0, 15, 15, 15, 15, 15, 8, 15, 4, 3, 0};
            /*30*/
            pattern_lookup[576] = /* id: 30, hash: 00000252 */new int[]{15, 2, 15, 7, 0, 3, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 82, 2, 2, 0};
            /*31*/
            pattern_lookup[129] = /* id: 31, hash: 000004a4 */new int[]{0, 3, 15, 8, 1, 4, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0};
            /*32*/
            pattern_lookup[258] = /* id: 32, hash: 00000948 */new int[]{1, 2, 4, 5, 15, 7, 9, 10, 0, 0, 15, 4, 15, 15, 15, 9, 72, 9, 3, 0};
            /*33*/
            pattern_lookup[135] = /* id: 33, hash: 000004a2 */new int[]{15, 0, 3, 4, 15, 6, 8, 9, 15, 15, 2, 2, 15, 15, 15, 8, 15, 4, 3, 0};
            /*34*/
            pattern_lookup[270] = /* id: 34, hash: 00000944 */new int[]{0, 1, 4, 5, 15, 7, 9, 10, 15, 0, 3, 3, 15, 15, 15, 9, 68, 9, 3, 0};
            /*35*/
            pattern_lookup[540] = /* id: 35, hash: 00001288 */new int[]{1, 2, 5, 6, 15, 8, 10, 11, 0, 0, 4, 4, 15, 15, 15, 10, 15, 18, 3, 0};
            /*36*/
            pattern_lookup[600] = /* id: 36, hash: 0000024a */new int[]{15, 15, 4, 7, 0, 2, 5, 8, 15, 15, 15, 15, 15, 15, 15, 15, 74, 2, 2, 0};
            /*37*/
            pattern_lookup[176] = /* id: 37, hash: 00000494 */new int[]{0, 15, 5, 8, 1, 3, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0};
            /*38*/
            pattern_lookup[353] = /* id: 38, hash: 00000928 */new int[]{1, 2, 15, 4, 6, 7, 9, 10, 0, 0, 15, 15, 15, 6, 15, 9, 40, 9, 3, 0};
            /*39*/
            pattern_lookup[182] = /* id: 39, hash: 00000492 */new int[]{15, 2, 5, 8, 0, 3, 6, 9, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4, 2, 0};
            /*40*/
            pattern_lookup[365] = /* id: 40, hash: 00000924 */new int[]{0, 3, 6, 9, 1, 4, 7, 10, 15, 15, 15, 15, 15, 15, 15, 15, 36, 9, 2, 0};
            /*41*/
            pattern_lookup[730] = /* id: 41, hash: 00001248 */new int[]{1, 2, 4, 5, 7, 8, 10, 11, 0, 0, 15, 4, 15, 7, 15, 10, 72, 18, 3, 0};
            /*42*/
            pattern_lookup[363] = /* id: 42, hash: 00000922 */new int[]{15, 0, 3, 4, 6, 7, 9, 10, 15, 15, 2, 2, 15, 6, 15, 9, 34, 9, 3, 0};
            /*43*/
            pattern_lookup[726] = /* id: 43, hash: 00001244 */new int[]{0, 1, 4, 5, 7, 8, 10, 11, 15, 0, 3, 3, 15, 7, 15, 10, 68, 18, 3, 0};
            /*44*/
            pattern_lookup[428] = /* id: 44, hash: 00002488 */new int[]{1, 2, 5, 6, 8, 9, 11, 12, 0, 0, 4, 4, 15, 8, 15, 11, 15, 36, 3, 0};
            /*45*/
            pattern_lookup[174] = /* id: 45, hash: 0000048a */new int[]{15, 0, 15, 2, 5, 6, 8, 9, 15, 15, 15, 15, 4, 4, 15, 8, 15, 4, 3, 0};
            /*46*/
            pattern_lookup[348] = /* id: 46, hash: 00000914 */new int[]{0, 1, 15, 3, 6, 7, 9, 10, 15, 0, 15, 15, 5, 5, 15, 9, 20, 9, 3, 0};
            /*47*/
            pattern_lookup[697] = /* id: 47, hash: 00001228 */new int[]{1, 2, 15, 4, 7, 8, 10, 11, 0, 0, 15, 15, 6, 6, 15, 10, 40, 18, 3, 0};
            /*48*/
            pattern_lookup[346] = /* id: 48, hash: 00000912 */new int[]{15, 0, 2, 3, 6, 7, 9, 10, 15, 15, 15, 2, 5, 5, 15, 9, 18, 9, 3, 0};
            /*49*/
            pattern_lookup[693] = /* id: 49, hash: 00001224 */new int[]{0, 1, 3, 4, 7, 8, 10, 11, 15, 0, 15, 3, 6, 6, 15, 10, 36, 18, 3, 0};
            /*50*/
            pattern_lookup[362] = /* id: 50, hash: 00002448 */new int[]{1, 2, 4, 5, 8, 9, 11, 12, 0, 0, 15, 4, 7, 7, 15, 11, 72, 36, 3, 0};
            /*51*/
            pattern_lookup[691] = /* id: 51, hash: 00001222 */new int[]{15, 0, 3, 4, 7, 8, 10, 11, 15, 15, 2, 2, 6, 6, 15, 10, 34, 18, 3, 0};
            /*52*/
            pattern_lookup[358] = /* id: 52, hash: 00002444 */new int[]{0, 1, 4, 5, 8, 9, 11, 12, 15, 0, 3, 3, 7, 7, 15, 11, 68, 36, 3, 0};
            /*53*/
            pattern_lookup[716] = /* id: 53, hash: 00004888 */new int[]{1, 2, 5, 6, 9, 10, 12, 13, 0, 0, 4, 4, 8, 8, 15, 12, 15, 72, 3, 0};
            /*54*/
            pattern_lookup[571] = /* id: 54, hash: 0000022a */new int[]{15, 0, 15, 2, 15, 4, 7, 8, 15, 15, 15, 15, 15, 15, 6, 6, 42, 2, 3, 0};
            /*55*/
            pattern_lookup[118] = /* id: 55, hash: 00000454 */new int[]{0, 1, 15, 3, 15, 5, 8, 9, 15, 0, 15, 15, 15, 15, 7, 7, 84, 4, 3, 0};
            /*56*/
            pattern_lookup[237] = /* id: 56, hash: 000008a8 */new int[]{1, 2, 15, 4, 15, 6, 9, 10, 0, 0, 15, 15, 15, 15, 8, 8, 15, 8, 3, 0};
            /*57*/
            pattern_lookup[112] = /* id: 57, hash: 00000452 */new int[]{15, 0, 2, 3, 15, 5, 8, 9, 15, 15, 15, 2, 15, 15, 7, 7, 82, 4, 3, 0};
            /*58*/
            pattern_lookup[225] = /* id: 58, hash: 000008a4 */new int[]{0, 1, 3, 4, 15, 6, 9, 10, 15, 0, 15, 3, 15, 15, 8, 8, 15, 8, 3, 0};
            /*59*/
            pattern_lookup[450] = /* id: 59, hash: 00001148 */new int[]{1, 2, 4, 5, 15, 7, 10, 11, 0, 0, 15, 4, 15, 15, 9, 9, 72, 17, 3, 0};
            /*60*/
            pattern_lookup[231] = /* id: 60, hash: 000008a2 */new int[]{15, 0, 3, 4, 15, 6, 9, 10, 15, 15, 2, 2, 15, 15, 8, 8, 15, 8, 3, 0};
            /*61*/
            pattern_lookup[462] = /* id: 61, hash: 00001144 */new int[]{0, 1, 4, 5, 15, 7, 10, 11, 15, 0, 3, 3, 15, 15, 9, 9, 68, 17, 3, 0};
            /*62*/
            pattern_lookup[924] = /* id: 62, hash: 00002288 */new int[]{1, 2, 5, 6, 15, 8, 11, 12, 0, 0, 4, 4, 15, 15, 10, 10, 15, 34, 3, 0};
            /*63*/
            pattern_lookup[104] = /* id: 63, hash: 0000044a */new int[]{15, 0, 15, 2, 4, 5, 8, 9, 15, 15, 15, 15, 15, 4, 7, 7, 74, 4, 3, 0};
            /*64*/
            pattern_lookup[208] = /* id: 64, hash: 00000894 */new int[]{0, 1, 15, 3, 5, 6, 9, 10, 15, 0, 15, 15, 15, 5, 8, 8, 15, 8, 3, 0};
            /*65*/
            pattern_lookup[417] = /* id: 65, hash: 00001128 */new int[]{1, 2, 15, 4, 6, 7, 10, 11, 0, 0, 15, 15, 15, 6, 9, 9, 40, 17, 3, 0};
            /*66*/
            pattern_lookup[214] = /* id: 66, hash: 00000892 */new int[]{15, 0, 2, 3, 5, 6, 9, 10, 15, 15, 15, 2, 15, 5, 8, 8, 15, 8, 3, 0};
            /*67*/
            pattern_lookup[429] = /* id: 67, hash: 00001124 */new int[]{0, 1, 3, 4, 6, 7, 10, 11, 15, 0, 15, 3, 15, 6, 9, 9, 36, 17, 3, 0};
            /*68*/
            pattern_lookup[858] = /* id: 68, hash: 00002248 */new int[]{1, 2, 4, 5, 7, 8, 11, 12, 0, 0, 15, 4, 15, 7, 10, 10, 72, 34, 3, 0};
            /*69*/
            pattern_lookup[427] = /* id: 69, hash: 00001122 */new int[]{15, 0, 3, 4, 6, 7, 10, 11, 15, 15, 2, 2, 15, 6, 9, 9, 34, 17, 3, 0};
            /*70*/
            pattern_lookup[854] = /* id: 70, hash: 00002244 */new int[]{0, 1, 4, 5, 7, 8, 11, 12, 15, 0, 3, 3, 15, 7, 10, 10, 68, 34, 3, 0};
            /*71*/
            pattern_lookup[684] = /* id: 71, hash: 00004488 */new int[]{1, 2, 5, 6, 8, 9, 12, 13, 0, 0, 4, 4, 15, 8, 11, 11, 15, 68, 3, 0};
            /*72*/
            pattern_lookup[206] = /* id: 72, hash: 0000088a */new int[]{15, 0, 15, 2, 5, 6, 9, 10, 15, 15, 15, 15, 4, 4, 8, 8, 15, 8, 3, 0};
            /*73*/
            pattern_lookup[412] = /* id: 73, hash: 00001114 */new int[]{0, 1, 15, 3, 6, 7, 10, 11, 15, 0, 15, 15, 5, 5, 9, 9, 20, 17, 3, 0};
            /*74*/
            pattern_lookup[825] = /* id: 74, hash: 00002228 */new int[]{1, 2, 15, 4, 7, 8, 11, 12, 0, 0, 15, 15, 6, 6, 10, 10, 40, 34, 3, 0};
            /*75*/
            pattern_lookup[410] = /* id: 75, hash: 00001112 */new int[]{15, 0, 2, 3, 6, 7, 10, 11, 15, 15, 15, 2, 5, 5, 9, 9, 18, 17, 3, 0};
            /*76*/
            pattern_lookup[821] = /* id: 76, hash: 00002224 */new int[]{0, 1, 3, 4, 7, 8, 11, 12, 15, 0, 15, 3, 6, 6, 10, 10, 36, 34, 3, 0};
            /*77*/
            pattern_lookup[618] = /* id: 77, hash: 00004448 */new int[]{1, 2, 4, 5, 8, 9, 12, 13, 0, 0, 15, 4, 7, 7, 11, 11, 72, 68, 3, 0};
            /*78*/
            pattern_lookup[819] = /* id: 78, hash: 00002222 */new int[]{15, 0, 3, 4, 7, 8, 11, 12, 15, 15, 2, 2, 6, 6, 10, 10, 34, 34, 3, 0};
            /*79*/
            pattern_lookup[614] = /* id: 79, hash: 00004444 */new int[]{0, 1, 4, 5, 8, 9, 12, 13, 15, 0, 3, 3, 7, 7, 11, 11, 68, 68, 3, 0};
            /*80*/
            pattern_lookup[1228] = /* id: 80, hash: 00008888 */new int[]{1, 2, 5, 6, 9, 10, 13, 14, 0, 0, 4, 4, 8, 8, 12, 12, 15, 15, 3, 0};
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

        static ByteVector weights = ByteVector.fromArray(SPECIES, new byte[]{10, 1, 10, 1, 10, 1, 10, 1, 100, 0, 100, 0, 100, 0, 100},
                0, VectorMask.fromLong(SPECIES, 0x7FFFL));
        static VectorMask<Byte> m = VectorMask.fromArray(SPECIES, new boolean[]{true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, false,}, 0);
        static VectorShuffle<Byte> shift1_shuffle = VectorShuffle.fromArray(SPECIES, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15}, 0);
        static VectorShuffle<Byte> shift8_shuffle = VectorShuffle.fromArray(SPECIES, new int[]{8, 9, 10, 11, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,}, 0);
        static VectorShuffle<Byte> shift_res_shuffle = VectorShuffle.fromArray(SPECIES, new int[]{0, 2, 4, 6, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,}, 0);
        static ByteVector dots = ByteVector.broadcast(SPECIES, '.');

        private int convertIPToIntVector(long cursor, long newlinePos) {
            long length = (newlinePos - cursor);
//            var line = chunk.asSlice(cursor, length);

            VectorMask<Byte> mask = VectorMask.fromLong(SPECIES, (1L << (length)) - 1);

            ByteVector chars = ByteVector.fromMemorySegment(SPECIES, chunk, cursor, ByteOrder.nativeOrder(), mask);

            VectorMask<Byte> dotMask = chars.eq(dots);
            chars = chars.sub((byte) '.', dotMask);

            VectorMask<Byte> numberMask = dotMask.not().and(mask);

            var numbers = chars.sub((byte) '0', numberMask); // TODO: try moving this in scalar

            long dotMaskLong = dotMask.toLong() | (1L << length);
            int hashcode = (int) ((dotMaskLong >> 5) ^ (dotMaskLong & 0x03ff));

            int maxLength = max_size_lookup[hashcode];
//            int index = pattern_lookup[hashcode];
            var pattern = pattern_lookup[hashcode];
            var shuffle = VectorShuffle.fromArray(SPECIES, pattern, 0);
            switch (maxLength) {
                case 1:
                    return numbers.rearrange(shuffle).reinterpretAsInts().lane(0);
                case 2:
                    var rearranged = numbers.rearrange(shuffle);
                    var lowerLong = rearranged.reinterpretAsLongs().lane(0);
                    var w1 = lowerLong >> 32;
                    var w2 = lowerLong & 0xffffffffL;
                    return (int) (10 * w2 + w1);
                case 3:
                    var rearranged2 = numbers.rearrange(shuffle);

                    var weighted = rearranged2.mul(weights);
                    var shift1 = weighted.rearrange(shift1_shuffle);

                    var partialSum = weighted.add(shift1, m);
                    partialSum = partialSum.mul(m.toVector().abs()); // TODO: optimize

                    var shift8 = weighted.rearrange(shift8_shuffle);
                    var aligned = shift8.add(partialSum);
                    var res = aligned.rearrange(shift_res_shuffle);
                    return res.reinterpretAsInts().lane(0);
            }

            throw new RuntimeException();
//            System.out.println(hashcode);
//
////
////            System.out.println(Arrays.toString(numberMask.toArray()));
//            System.out.println(Arrays.toString(chars.toArray()));
//
//            return chars.reduceLanes(VectorOperators.OR);
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

        record CursorNum(long cursor, int num) {
        }

        private CursorNum parseNumber(long cursor, long newlinePos) {
            int octet = chunk.get(JAVA_BYTE, cursor) * 10 - '0' * 10; // TODO
            cursor++;
            if (cursor == newlinePos) {
                return new CursorNum(cursor, octet / 10);
            }
            var nextChar = chunk.get(JAVA_BYTE, cursor);
            if (nextChar == '.') {
                return new CursorNum(cursor, octet / 10);
            }
            octet += nextChar - '0';

            cursor++;
            if (cursor == newlinePos) {
                return new CursorNum(cursor, octet);
            }
            nextChar = chunk.get(JAVA_BYTE, cursor);
            if (nextChar == '.') {
                return new CursorNum(cursor, octet);
            }

            octet = octet * 10 + nextChar - '0';

            return new CursorNum(cursor + 1, octet);
        }
    }

    public static class MaxBitSet {
        private static final int ADDRESS_BITS_PER_WORD = 6;

        /**
         * The internal field corresponding to the serialField "bits".
         */
        // todo atomic
        private final AtomicLongArray words;

        /**
         * Given a bit index, return word index containing it.
         */
        private static int wordIndex(int bitIndex) {
            return bitIndex >>> ADDRESS_BITS_PER_WORD;
        }

        public MaxBitSet() {
            words = new AtomicLongArray(67108864);
        }

        /**
         * Sets the bit at the specified index to {@code true}.
         */
//        public void set(int bitIndex) {
//            int wordIndex = wordIndex(bitIndex);
//            words[wordIndex] |= (1L << bitIndex); // Restores invariants
//        }

        public void set(int bitIndex) {
            int wordIndex = wordIndex(bitIndex);
            while (true) {
                long num = words.get(wordIndex);
                // In Java, doing a modulus has no effect as the shift is &63 for long
                // In other words `1 << bitIndex` is equivalent to `1 << (bitIndex % 64)`
                long numSet = num | (1L << bitIndex);
                if (num == numSet || words.compareAndSet(wordIndex, num, numSet))
                    return;
            }
//            words[wordIndex] |=; // Restores invariants
        }

        public long count() {
            long count = 0;
            for (int i = 0; i < words.length(); i++) {
                count += Long.bitCount(words.get(i));
            }

            return count;
        }
    }
}
