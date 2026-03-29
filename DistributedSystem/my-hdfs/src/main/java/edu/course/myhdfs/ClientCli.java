package edu.course.myhdfs;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String nameNode = System.getProperty("namenode", "http://127.0.0.1:9000");
        String command = args[0];

        switch (command) {
            case "write" -> write(nameNode, args);
            case "readall" -> readAll(nameNode, args);
            case "demo" -> demo(nameNode, args);
            default -> printUsage();
        }
    }

    private static void write(String nameNode, String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return;
        }
        String file = args[1];
        String value = args[2];
        Models.ConsistencyMode mode = Models.ConsistencyMode.from(args[3]);

        Models.MetadataResponse metadata = fetchMetadata(nameNode, file);

        String endpoint = metadata.primaryUrl + "/write?file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Consistency-Mode", mode.name());
        headers.put("X-Client-Id", "client-1");
        headers.put("X-Seq", String.valueOf(System.nanoTime()));

        String resp = HttpUtil.put(endpoint, value, headers, 3000, 15000);
        System.out.println("WRITE_ACK=" + resp);
    }

    private static void readAll(String nameNode, String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }
        String file = args[1];
        List<Models.ReplicaInfo> replicas = fetchReplicas(nameNode);

        for (Models.ReplicaInfo replica : replicas) {
            String endpoint = replica.baseUrl + "/read?file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
            try {
                String response = HttpUtil.get(endpoint, 3000, 5000);
                System.out.println(replica.id + " -> " + response);
            } catch (Exception e) {
                System.out.println(replica.id + " -> ERROR: " + e.getMessage());
            }
        }
    }

    private static void demo(String nameNode, String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        String file = args[1];
        String value = args[2];

        System.out.println("[1] async_observe write...");
        write(nameNode, new String[]{"write", file, value, "async_observe"});

        System.out.println("[2] immediate read all replicas (expect inconsistency)...");
        readAll(nameNode, new String[]{"readall", file});

        Thread.sleep(3500);
        System.out.println("[3] read all replicas after settle (expect convergence)...");
        readAll(nameNode, new String[]{"readall", file});
    }

    private static Models.MetadataResponse fetchMetadata(String nameNode, String file) throws Exception {
        String endpoint = nameNode + "/allocate?file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
        String response = HttpUtil.get(endpoint, 3000, 5000);
        return Jsons.GSON.fromJson(response, Models.MetadataResponse.class);
    }

    private static List<Models.ReplicaInfo> fetchReplicas(String nameNode) throws Exception {
        String response = HttpUtil.get(nameNode + "/replicas", 3000, 5000);
        Models.ReplicaInfo[] arr = Jsons.GSON.fromJson(response, Models.ReplicaInfo[].class);
        return List.of(arr);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='write <file> <value> <sync|async_observe>' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='readall <file>' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='demo <file> <value>' exec:java");
        System.out.println("System property:");
        System.out.println("  -Dnamenode=http://127.0.0.1:9000");
    }
}
