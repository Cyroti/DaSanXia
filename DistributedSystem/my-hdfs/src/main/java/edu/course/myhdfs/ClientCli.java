package edu.course.myhdfs;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            case "put" -> put(nameNode, args);
            case "get" -> get(nameNode, args);
            case "ls" -> listFiles(nameNode);
            case "inspect" -> inspect(nameNode, args);
            case "demo" -> demo(nameNode, args);
            default -> printUsage();
        }
    }

    private static void put(String nameNode, String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        String file = args[1];
        String payload = args[2];
        int blockSize = args.length >= 4 ? Integer.parseInt(args[3]) : 8;
        int replication = args.length >= 5 ? Integer.parseInt(args[4]) : 3;
        Models.ConsistencyMode mode = args.length >= 6
                ? Models.ConsistencyMode.from(args[5])
                : Models.ConsistencyMode.SYNC;

        Models.AllocateRequest allocateReq = new Models.AllocateRequest();
        allocateReq.file = file;
        allocateReq.blockSize = blockSize;
        allocateReq.replication = replication;
        allocateReq.fileSizeBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        Models.AllocationResponse allocation = allocate(nameNode, allocateReq);

        List<String> chunks = splitByBytes(payload, allocation.blockSize);
        if (chunks.size() < allocation.blocks.size()) {
            while (chunks.size() < allocation.blocks.size()) {
                chunks.add("");
            }
        }

        for (Models.BlockMetadata block : allocation.blocks) {
            String chunk = chunks.get(block.index);
            block.sizeBytes = chunk.getBytes(StandardCharsets.UTF_8).length;
            writeBlock(block, file, chunk, mode);
        }

        Models.CommitRequest commitReq = new Models.CommitRequest();
        commitReq.file = file;
        commitReq.fileSizeBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        commitReq.blockSize = allocation.blockSize;
        commitReq.replication = allocation.replication;
        commitReq.blocks = allocation.blocks;

        Models.FileMetadata committed = commit(nameNode, commitReq);
        System.out.println("PUT_OK file=" + committed.file + " blocks=" + committed.blocks.size() + " replication=" + committed.replication + " mode=" + mode);
    }

    private static void get(String nameNode, String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        String file = args[1];
        Models.FileMetadata meta = fetchFileMetadata(nameNode, file);
        StringBuilder content = new StringBuilder();

        for (Models.BlockMetadata block : meta.blocks) {
            String chunk = readFromReplicas(block, file);
            content.append(chunk);
        }

        System.out.println(content);
    }

    private static void inspect(String nameNode, String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }
        String file = args[1];
        Models.FileMetadata meta = fetchFileMetadata(nameNode, file);

        for (Models.BlockMetadata block : meta.blocks) {
            System.out.println("BLOCK " + block.index + " " + block.blockId);
            for (Models.ReplicaInfo replica : block.replicas) {
                String endpoint = replica.baseUrl
                        + "/api/v1/blocks/"
                        + URLEncoder.encode(block.blockId, StandardCharsets.UTF_8)
                        + "?file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
                try {
                    String response = HttpUtil.get(endpoint, 3000, 5000);
                    System.out.println("  " + replica.id + " -> " + response);
                } catch (Exception e) {
                    System.out.println("  " + replica.id + " -> ERROR: " + e.getMessage());
                }
            }
        }
    }

    private static void listFiles(String nameNode) throws Exception {
        String response = HttpUtil.get(nameNode + "/api/v1/files", 3000, 5000);
        Models.FileListResponse list = Jsons.GSON.fromJson(response, Models.FileListResponse.class);
        if (list.files == null || list.files.isEmpty()) {
            System.out.println("(empty)");
            return;
        }
        for (Models.FileMetadata file : list.files) {
            System.out.println(file.file + " blocks=" + file.blocks.size() + " replication=" + file.replication + " blockSize=" + file.blockSize);
        }
    }

    private static void demo(String nameNode, String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        String file = args[1];
        String value = args[2];

        System.out.println("[1] async_observe put...");
        put(nameNode, new String[]{"put", file, value, "8", "3", "async_observe"});

        System.out.println("[2] immediate inspect (expect inconsistency)...");
        inspect(nameNode, new String[]{"inspect", file});

        Thread.sleep(3500);
        System.out.println("[3] inspect after settle (expect convergence)...");
        inspect(nameNode, new String[]{"inspect", file});

        System.out.println("[4] reconstructed file:");
        get(nameNode, new String[]{"get", file});
    }

    private static Models.AllocationResponse allocate(String nameNode, Models.AllocateRequest req) throws Exception {
        String response = HttpUtil.put(nameNode + "/api/v1/files/allocate", Jsons.GSON.toJson(req), Map.of(), 3000, 10000);
        return Jsons.GSON.fromJson(response, Models.AllocationResponse.class);
    }

    private static Models.FileMetadata commit(String nameNode, Models.CommitRequest req) throws Exception {
        String response = HttpUtil.put(nameNode + "/api/v1/files/commit", Jsons.GSON.toJson(req), Map.of(), 3000, 10000);
        return Jsons.GSON.fromJson(response, Models.FileMetadata.class);
    }

    private static Models.FileMetadata fetchFileMetadata(String nameNode, String file) throws Exception {
        String endpoint = nameNode + "/api/v1/files/" + URLEncoder.encode(file, StandardCharsets.UTF_8);
        String response = HttpUtil.get(endpoint, 3000, 5000);
        return Jsons.GSON.fromJson(response, Models.FileMetadata.class);
    }

    private static void writeBlock(Models.BlockMetadata block, String file, String chunk, Models.ConsistencyMode mode) throws Exception {
        if (block.replicas == null || block.replicas.isEmpty()) {
            throw new IllegalStateException("block has no replicas: " + block.blockId);
        }

        Models.ReplicaInfo head = block.replicas.get(0);
        List<String> remain = new ArrayList<>();
        for (int i = 1; i < block.replicas.size(); i++) {
            remain.add(block.replicas.get(i).baseUrl);
        }

        String endpoint = head.baseUrl + "/api/v1/blocks/" + URLEncoder.encode(block.blockId, StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Consistency-Mode", mode.name());
        headers.put("X-Replica-Chain", String.join(",", remain));

        Models.BlockWriteRequest req = new Models.BlockWriteRequest();
        req.file = file;
        req.index = block.index;
        req.sizeBytes = chunk.getBytes(StandardCharsets.UTF_8).length;
        req.payload = chunk;

        HttpUtil.put(endpoint, Jsons.GSON.toJson(req), headers, 3000, 15000);
    }

    private static String readFromReplicas(Models.BlockMetadata block, String file) throws Exception {
        Exception last = null;
        for (Models.ReplicaInfo replica : block.replicas) {
            String endpoint = replica.baseUrl
                    + "/api/v1/blocks/"
                    + URLEncoder.encode(block.blockId, StandardCharsets.UTF_8)
                    + "?file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
            try {
                String response = HttpUtil.get(endpoint, 3000, 5000);
                Models.BlockReadResult r = Jsons.GSON.fromJson(response, Models.BlockReadResult.class);
                if (r.exists) {
                    return r.payload;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("block not found across replicas: " + block.blockId);
    }

    private static List<String> splitByBytes(String payload, int blockSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < payload.length()) {
            int end = Math.min(payload.length(), start + blockSize);
            chunks.add(payload.substring(start, end));
            start = end;
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='put <file> <content> [blockSize] [replication] [sync|async_observe]' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='get <file>' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='inspect <file>' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='ls' exec:java");
        System.out.println("  mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='demo <file> <content>' exec:java");
        System.out.println("System property:");
        System.out.println("  -Dnamenode=http://127.0.0.1:9000");
    }
}
