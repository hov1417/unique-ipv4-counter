import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class IPv4ParseVector {
    private static final Unsafe UNSAFE = unsafe();
    private static final long MAGIC_MULTIPLIER_0 = 0x1 + 10 * 0x100L + 0x10000L * 100;
    private static final long MAGIC_MULTIPLIER_2_0 = 0x1 + 10 * 0x100L;
    private static final long MAGIC_MULTIPLIER_2_1 = 0x1_00_00 + 10 * 0x1_00_00_00L;
    private static final long MAGIC_MULTIPLIER_1 = 0x1 + 10 * 0x10000L + 0x1000000 * 100;
    private static final long MAGIC_MULTIPLIER_2 = 0x1 + 10 * 0x1000000L + 0x100000000L * 100;
    private static final long MAGIC_MULTIPLIER_3 = 0x1 + 10 * 0x100000000L + 0x10000000000L * 100;

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

        if (Objects.equals(args[1], "new")) {
            main2(args[0]);
        } else if (Objects.equals(args[1], "check")) {
            mainCheck(args[0]);
        } else {
            mainBase(args[0]);
        }
    }

    private static void mainCheck(String filePath) throws IOException {
        final File file = new File(filePath);
        final long length = file.length();
        try (var raf = new RandomAccessFile(file, "r")) {
            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            var worker = new ChunkProcessor(mappedFile.asSlice(0, length));
            for (var cursor = 0L; cursor < worker.chunk.byteSize(); ) {
                var newlinePos = worker.findByte(cursor, '\n');
                int ipInt = worker.convertIPToIntSWAR2333_v2(cursor);
                int ipIntNaive = worker.convertIPToNaive(cursor, newlinePos);
                if (ipInt != ipIntNaive) {
                    System.out.println(Long.toHexString(ipInt ^ ipIntNaive));
                }
                cursor = newlinePos + 1;
            }
        }
    }

    private static void mainBase(String filePath) throws IOException {
        final File file = new File(filePath);
        final long length = file.length();
        try (var raf = new RandomAccessFile(file, "r")) {
            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            var worker = new ChunkProcessor(mappedFile.asSlice(0, length));
            for (var cursor = 0L; cursor < worker.chunk.byteSize(); ) {
                var newlinePos = worker.findByte(cursor, '\n');
                int ipInt = worker.convertIPToIntSWAR2333(cursor);
                cursor = newlinePos + 1;
            }
        }
    }

    private static void main2(String filePath) throws IOException {
        final File file = new File(filePath);
        final long length = file.length();
        try (var raf = new RandomAccessFile(file, "r")) {
            final var mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, length, Arena.global());
            var worker = new ChunkProcessor(mappedFile.asSlice(0, length));
            for (var cursor = 0L; cursor < worker.chunk.byteSize(); ) {
                var newlinePos = worker.findByte(cursor, '\n');
                int ipInt = worker.convertIPToIntSWAR2333_v2(cursor);
                cursor = newlinePos + 1;
            }
        }
    }

    private static final class ChunkProcessor {

        private final long address;

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

        private final MemorySegment chunk;

        private ChunkProcessor(MemorySegment chunk) {
            this.chunk = chunk;
            this.address = chunk.address();
        }

        private int convertIPToIntSWAR2333_v2(long cursor) {
            long value1 = UNSAFE.getLong(address + cursor) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;
            long value2 = UNSAFE.getLong(address + cursor + 8) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;

            return (int) (
                    (((value1 * MAGIC_MULTIPLIER_2_1) & 0xFF_00_00_00L))
                            | (((value1 >>> 24) * MAGIC_MULTIPLIER_0) & 0xFF_00_00L)
                            | (((((value1 >>> 56) | (value2 << 8)) * MAGIC_MULTIPLIER_0) >> 8) & 0xFF_00L)
                            | ((((value2 >>> 24) * MAGIC_MULTIPLIER_0) >>> 16) & 0xFFL)
            );
        }

        private int convertIPToIntSWAR2333(long cursor) {
            long value1 = UNSAFE.getLong(address + cursor) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;
            long value2 = UNSAFE.getLong(address + cursor + 8) & 0x0F_0F_0F_0F_0F_0F_0F_0FL;
            return (int) (
                    ((((value1 & 0xFF) * 2560) + (value1 & 0xFF00)) << 16)
                            | ((((((value1 >>> 8) & 0xFF0000) * 100)) + (((value1 >>> 16) & 0xFF0000) * 10) + ((value1 >>> 24) & 0xFF0000)))
                            | (((((value1 >>> 56) & 0xFF) * 25600) + ((value2 & 0xFF) * 2560) + ((value2) & 0xFF00)))
                            | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF)))
            );
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

        public MemorySegment chunk() {
            return chunk;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ChunkProcessor) obj;
            return Objects.equals(this.chunk, that.chunk);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunk);
        }

        @Override
        public String toString() {
            return "ChunkProcessor[" +
                    "chunk=" + chunk + ']';
        }

    }
}
