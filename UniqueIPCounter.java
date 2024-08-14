import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class UniqueIPCounter {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java UniqueIPCounter <file_path>");
            return;
        }

        String filePath = args[0];
        var uniqueIPs = new MaxBitSet();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String ipLine = line;
                executor.execute(() -> {
                    int ipInt = convertIPToBigEndian2(ipLine);
                    uniqueIPs.set(ipInt);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            // Wait for all threads to finish
        }

        System.out.println("Number of unique IP addresses: " + uniqueIPs.count());
    }

    private static int convertIPToBigEndian(String ip) {
        String[] parts = ip.split("\\.");
        int ipInt = 0;

        for (String part : parts) {
            int value = Integer.parseInt(part);
            ipInt = (ipInt << 8) | value;
        }

        return ipInt;
    }

    private static int convertIPToBigEndian2(String ip) {
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

    public static class MaxBitSet {
        private static final int ADDRESS_BITS_PER_WORD = 6;

        /**
         * The internal field corresponding to the serialField "bits".
         */
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
