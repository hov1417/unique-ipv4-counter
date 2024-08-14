import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UniqueIPCounter {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java UniqueIPCounter <file_path>");
            return;
        }

        String filePath = args[0];
        ConcurrentHashMap<Integer, Boolean> uniqueIPs = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String ipLine = line;
                executor.execute(() -> {
                    int ipInt = convertIPToBigEndian(ipLine);
                    uniqueIPs.put(ipInt, true);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            // Wait for all threads to finish
        }

        System.out.println("Number of unique IP addresses: " + uniqueIPs.size());
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
}
