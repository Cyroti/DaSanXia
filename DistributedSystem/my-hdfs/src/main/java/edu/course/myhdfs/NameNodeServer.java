package edu.course.myhdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NameNodeServer {
    private final int port;
    private final List<Models.ReplicaInfo> dataNodes;
    private final NameNodeMetadataStore metadataStore;
    private final Map<String, Models.FileMetadata> files = new ConcurrentHashMap<>();
    private final AtomicInteger placementCursor = new AtomicInteger(0);
    private HttpServer server;

    public NameNodeServer(int port, List<Models.ReplicaInfo> dataNodes, Path metadataPath) {
        this.port = port;
        this.dataNodes = List.copyOf(dataNodes);
        this.metadataStore = new NameNodeMetadataStore(metadataPath);
    }

    public void start() throws IOException {
        files.putAll(metadataStore.load());

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/health", this::handleHealth);
        server.createContext("/api/v1/datanodes", this::handleDataNodes);
        server.createContext("/api/v1/files/allocate", this::handleAllocate);
        server.createContext("/api/v1/files/commit", this::handleCommit);
        server.createContext("/api/v1/files", this::handleFiles);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("NameNode started on :" + port + ", dataNodes=" + Jsons.GSON.toJson(dataNodes));
    }

    private void handleDataNodes(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        HttpUtil.sendJson(exchange, 200, dataNodes);
    }

    private void handleAllocate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                && !"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Models.AllocateRequest req = Jsons.GSON.fromJson(HttpUtil.readBody(exchange), Models.AllocateRequest.class);
        if (req == null || req.file == null || req.file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "invalid request: missing file");
            return;
        }

        int blockSize = req.blockSize > 0 ? req.blockSize : 1024;
        int replication = req.replication > 0 ? req.replication : 3;
        int fileSize = Math.max(req.fileSizeBytes, 0);

        if (dataNodes.isEmpty()) {
            HttpUtil.sendText(exchange, 500, "no datanodes configured");
            return;
        }

        Models.AllocationResponse resp = allocate(req.file, fileSize, blockSize, replication);
        HttpUtil.sendJson(exchange, 200, resp);
    }

    private void handleCommit(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                && !"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Models.CommitRequest req = Jsons.GSON.fromJson(HttpUtil.readBody(exchange), Models.CommitRequest.class);
        if (req == null || req.file == null || req.file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "invalid request: missing file");
            return;
        }
        if (req.blocks == null || req.blocks.isEmpty()) {
            HttpUtil.sendText(exchange, 400, "invalid request: blocks is empty");
            return;
        }

        Models.FileMetadata meta = new Models.FileMetadata();
        meta.file = req.file;
        meta.fileSizeBytes = Math.max(req.fileSizeBytes, 0);
        meta.blockSize = req.blockSize > 0 ? req.blockSize : 1024;
        meta.replication = Math.max(req.replication, 1);
        long now = System.currentTimeMillis();
        Models.FileMetadata old = files.get(req.file);
        meta.createdAt = old == null ? now : old.createdAt;
        meta.updatedAt = now;
        meta.blocks = new ArrayList<>(req.blocks);

        files.put(req.file, meta);
        metadataStore.save(files);
        HttpUtil.sendJson(exchange, 200, meta);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        HttpUtil.sendText(exchange, 200, "ok");
    }

    private void handleFiles(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/v1/files";
        if (prefix.equals(path)) {
            Models.FileListResponse resp = new Models.FileListResponse();
            List<Models.FileMetadata> sorted = new ArrayList<>(files.values());
            sorted.sort((a, b) -> a.file.compareToIgnoreCase(b.file));
            resp.files = sorted;
            HttpUtil.sendJson(exchange, 200, resp);
            return;
        }

        String marker = "/api/v1/files/";
        if (!path.startsWith(marker) || path.length() <= marker.length()) {
            HttpUtil.sendText(exchange, 404, "Not Found");
            return;
        }

        String encoded = path.substring(marker.length());
        String file = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        Models.FileMetadata meta = files.get(file);
        if (meta == null) {
            HttpUtil.sendText(exchange, 404, "file not found: " + file);
            return;
        }
        HttpUtil.sendJson(exchange, 200, meta);
    }

    private Models.AllocationResponse allocate(String file, int fileSize, int blockSize, int replication) {
        int blockCount = Math.max(1, (fileSize + blockSize - 1) / blockSize);
        int realReplication = Math.min(Math.max(replication, 1), dataNodes.size());

        Models.AllocationResponse resp = new Models.AllocationResponse();
        resp.file = file;
        resp.blockSize = blockSize;
        resp.replication = realReplication;
        resp.blockCount = blockCount;

        int cursor = placementCursor.getAndIncrement();
        for (int i = 0; i < blockCount; i++) {
            Models.BlockMetadata block = new Models.BlockMetadata();
            block.index = i;
            block.blockId = safeFileToken(file) + "-b" + i;
            int remain = fileSize - i * blockSize;
            block.sizeBytes = remain > blockSize ? blockSize : Math.max(remain, 0);

            List<Models.ReplicaInfo> chosen = chooseReplicas(cursor + i, realReplication);
            block.replicas = chosen;
            resp.blocks.add(block);
        }
        return resp;
    }

    private List<Models.ReplicaInfo> chooseReplicas(int start, int replication) {
        if (dataNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Models.ReplicaInfo> list = new ArrayList<>();
        for (int i = 0; i < replication; i++) {
            int idx = Math.floorMod(start + i, dataNodes.size());
            list.add(dataNodes.get(idx));
        }
        return list;
    }

    private static String safeFileToken(String file) {
        String token = file.replace('\\', '_').replace('/', '_').replace(' ', '_');
        return token.isBlank() ? "file" : token;
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        String replicasArg = "dn1=http://127.0.0.1:9001,dn2=http://127.0.0.1:9002,dn3=http://127.0.0.1:9003";
        Path metadataPath = Path.of("data/namenode/metadata.json");

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--replicas=")) {
                replicasArg = arg.substring("--replicas=".length());
            } else if (arg.startsWith("--metadataPath=")) {
                metadataPath = Path.of(arg.substring("--metadataPath=".length()));
            }
        }

        List<Models.ReplicaInfo> replicas = parseReplicas(replicasArg);
        if (replicas.isEmpty()) {
            throw new IllegalArgumentException("No replicas configured");
        }

        new NameNodeServer(port, replicas, metadataPath).start();
    }

    static List<Models.ReplicaInfo> parseReplicas(String raw) {
        List<Models.ReplicaInfo> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return list;
        }
        String[] items = raw.split(",");
        for (String item : items) {
            String[] kv = item.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            list.add(new Models.ReplicaInfo(kv[0].trim(), kv[1].trim()));
        }
        return list;
    }
}
