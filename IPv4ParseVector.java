import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;

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
                int ipInt = convertIPToIntVector(cursor, newlinePos);
                int ipIntNaive = convertIPToNaive(cursor, newlinePos);
                System.out.println(ipInt + " " + ipIntNaive + " " + (ipInt == ipIntNaive));
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

        private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_128;
        private static final IntVector OFFSETS = IntVector.fromArray(SPECIES, new int[]{24, 16, 8, 0}, 0);

        private int convertIPToIntVector(long cursor, long newlinePos) {
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

    }
}
