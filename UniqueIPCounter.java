import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

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
        System.out.println("size " + length);
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
                int ipInt = convertIPToBigEndian7(cursor, newlinePos);
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

        private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_128;
        private static final IntVector OFFSETS = IntVector.fromArray(SPECIES, new int[]{24, 16, 8, 0}, 0);

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

            IntVector vector = IntVector.fromArray(SPECIES, bytes, 0).lanewise(VectorOperators.LSHL, OFFSETS);

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

            IntVector vector = IntVector.fromArray(SPECIES, bytes, 0).lanewise(VectorOperators.LSHL, OFFSETS);

            return vector.reduceLanes(VectorOperators.OR);
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
        private final long[] words;

        /**
         * Given a bit index, return word index containing it.
         */
        private static int wordIndex(int bitIndex) {
            return bitIndex >>> ADDRESS_BITS_PER_WORD;
        }

        public MaxBitSet() {
            words = new long[67108864];
        }

        /**
         * Sets the bit at the specified index to {@code true}.
         */
        public void set(int bitIndex) {
            int wordIndex = wordIndex(bitIndex);
            words[wordIndex] |= (1L << bitIndex); // Restores invariants
        }

        public long count() {
            long count = 0;
            for (long word : this.words) {
                count += Long.bitCount(word);
            }
            return count;
        }
    }
}
