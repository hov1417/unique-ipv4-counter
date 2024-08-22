import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;


public class UniqueIPCounter {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final Unsafe UNSAFE = unsafe();
    private static final AtomicInteger chunk_id = new AtomicInteger(0);
    private static final long CHUNK_SIZE = 8 << 20; // 8MB
    private static final MaxBitSet uniqueIPs = new MaxBitSet();
    private static long start_addr, end_addr;
    private static int CHUNK_COUNT;

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void spawnWorker() throws IOException {
        ProcessHandle.Info info = ProcessHandle.current().info();
        ArrayList<String> workerCommand = new ArrayList<>();
        info.command().ifPresent(workerCommand::add);
        workerCommand.add("--worker");
        info.arguments().ifPresent(args -> workerCommand.addAll(Arrays.asList(args)));
        Process process = new ProcessBuilder()
                .command(workerCommand)
                .start();
        process
                .getInputStream()
                .transferTo(System.out);
//        uncomment when debugging
        process
                .getErrorStream()
                .transferTo(System.out);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0 || !("--worker".equals(args[0]))) {
            spawnWorker();
            return;
        }
        if (args.length < 2) {
            System.out.println("Usage: java UniqueIPCounter <file_path>");
            return;
        }

        String filePath = args[1];

        var file = FileChannel.open(Path.of(filePath), StandardOpenOption.READ);
        long length = file.size();
        start_addr = file.map(MapMode.READ_ONLY, 0, length, Arena.global()).address();
        end_addr = start_addr + length;

        CHUNK_COUNT = (int) Math.ceilDiv(length, CHUNK_SIZE);
        for (var w : IntStream.range(0, length < 1024 * 1024 ? 1 : NUM_THREADS).mapToObj(i -> new Worker()).toList()) {
            w.join();
        }

        System.out.println("Number of unique IP addresses: " + uniqueIPs.count());
    }


    /**
     * Get dot positions mask
     *
     * @param word word to search in
     * @return long mask, where if byte was '0x0E' then in result it is '0x80'
     */
    static long getDotCode(final long word) {
        final long reversed = word ^ 0x0E0E0E0E0E0E0E0EL; // xor with ........
        return (reversed - 0x0101010101010101L) & (~reversed & 0x8080808080808080L);
    }

    /**
     * Get new line position mask
     *
     * @param word word to search in
     * @return long mask, where if byte was '0x0A' then in result it is '0x80'
     */
    static long getLFCode(final long word) {
        final long reversed = word ^ 0x0A0A0A0A0A0A0A0AL; // xor with \n\n\n\n\n\n\n\n
        return (reversed - 0x0101010101010101L) & (~reversed & 0x8080808080808080L);
    }

    /**
     * Find next line address
     *
     * @param address start address
     * @return long mask, where if byte was '0x0A' then in result it is '0x80'
     */
    static long nextLF(long address) {
        long word = UNSAFE.getLong(address);
        long lfpos_code = getLFCode(word);

        // if it has a second byte
        if (lfpos_code == 0) {
            address += 8;
            word = UNSAFE.getLong(address);
            lfpos_code = getLFCode(word);
        }
        return address + (Long.numberOfTrailingZeros(lfpos_code) >>> 3) + 1;
    }

    // Parse number
    static int num(long w, long d) {
//      length from 7 (1.1.1.1) to 15 (111.111.111.111) so it is either 1 or 2 longs
        // d - w == 8 case is only when x.x.x.x
        // TODO bench with removed branch
//        if (d - w == 8) {
//            var value = UNSAFE.getLong(w);
//            return (int) ((value & 0x00_00_00_00_00_00_00_0FL) |
//                    ((value & 0x00_00_00_00_00_0F_00_00L) >> 8) |
//                    ((value & 0x00_00_00_0F_00_00_00_00L) >> 16) |
//                    ((value & 0x00_0F_00_00_00_00_00_00L) >> 24)
//            );
//        } else {
        var value1 = UNSAFE.getLong(w) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;
        var value2 = UNSAFE.getLong(w + 8) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;
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
        var endIndex = d - w - 9; // Long.numberOfTrailingZeros(getLFCode(value2)) >> 3;
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

    private static class Worker extends Thread {

        public Worker() {
            this.setPriority(Thread.MAX_PRIORITY);
            this.start();
        }

        @Override
        public void run() {
            int id;
            while ((id = chunk_id.getAndIncrement()) < CHUNK_COUNT) {
                long address = start_addr + id * CHUNK_SIZE;
                long end = Math.min(address + CHUNK_SIZE, end_addr);

                // find start of line
                if (id > 0) {
                    address = nextLF(address);
                }

                while (address < end) {
                    long lineStart = address;
                    long nextStart = nextLF(lineStart + 1);
                    uniqueIPs.set(num(lineStart, nextStart));
                    address = nextStart;
                }
            }
        }
    }

    /**
     * A wrapper class around AtomicLongArray that acts as a bitmask.
     * It pre-allocates enough memory to support any integer index, so it can handle all possible bit positions.
     */
    public final static class MaxBitSet {

        /**
         * The internal "bits" representation
         */
        private final AtomicLongArray words;

        /**
         * Given a bit index, return word index containing it.
         */
        private static int wordIndex(int bitIndex) {
            return bitIndex >>> 6;
        }

        public MaxBitSet() {
            // 67108864 = 2^26 = 2^32 / 2^6
            // 32 is byte size of integer representation of IP Addresses
            // 6 is byte size of the type long
            words = new AtomicLongArray(67108864);
        }

        /**
         * Sets the bit at the specified index to {@code true}.
         */
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
        }

        /**
         * @return Number of bits that were set in this MaxBitSet
         */
        public long count() {
            // making this parallel adds too much overhead
            // difference is either statistically insignificantly faster or even slower on some runs
            long count = 0;
            for (int i = 0; i < words.length(); i++) {
                count += Long.bitCount(words.get(i));
            }
            return count;
        }
    }
}
