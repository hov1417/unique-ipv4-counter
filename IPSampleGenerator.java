import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class IPSampleGenerator {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java IPSampleGenerator <sample_size> <output_file>");
            return;
        }

        int sampleSize = Integer.parseInt(args[0]);
        String outputFile = args[1];

        try (FileWriter writer = new FileWriter(outputFile)) {
            for (int i = 0; i < sampleSize; i++) {
                writer.write(generateRandomIP() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Generated " + sampleSize + " random IP addresses in " + outputFile);
    }

    private static String generateRandomIP() {
        Random random = new Random();
        return random.nextInt(256) + "." +
                random.nextInt(256) + "." +
                random.nextInt(256) + "." +
                random.nextInt(256);
    }
}
