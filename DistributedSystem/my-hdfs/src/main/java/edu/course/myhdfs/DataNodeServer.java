package edu.course.myhdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class DataNodeServer {
    private final String nodeId;
    private final int port;
    private final Path storageDir;
    private final long forwardDelayMs;
    private final long throttleBytesPerSec;

    private final Map<String, ReentrantLock> blockLocks = new ConcurrentHashMap<>();

    private final ExecutorService requestPool = Executors.newFixedThreadPool(12);
    private final ExecutorService asyncForwardPool = Executors.newFixedThreadPool(4);

    private HttpServer server;

    public DataNodeServer(String nodeId,
                          int port,
                          Path storageDir,
                          long forwardDelayMs,
                          long throttleBytesPerSec) {
        this.nodeId = nodeId;
        this.port = port;
        this.storageDir = storageDir;
        this.forwardDelayMs = Math.max(0L, forwardDelayMs);
        this.throttleBytesPerSec = Math.max(0L, throttleBytesPerSec);
    }

    public void start() throws IOException {
        Files.createDirectories(storageDir);
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/blocks", this::handleBlocks);
        server.createContext("/api/v1/state", this::handleState);
        server.createContext("/api/v1/health", this::handleHealth);
        server.setExecutor(requestPool);
        server.start();
        System.out.printf("DataNode %s started on :%d, delayMs=%d, throttleBps=%d, dir=%s%n",
                nodeId, port, forwardDelayMs, throttleBytesPerSec, storageDir);
    }

    private void handleBlocks(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/v1/blocks/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            HttpUtil.sendText(exchange, 404, "Not Found");
            return;
        }

        String blockId = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleBlockWrite(exchange, blockId);
            return;
        }
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleBlockRead(exchange, blockId);
            return;
        }
        HttpUtil.sendText(exchange, 405, "Method Not Allowed");
    }

    private void handleBlockWrite(HttpExchange exchange, String blockId) throws IOException {
        Models.BlockWriteRequest req = Jsons.GSON.fromJson(HttpUtil.readBody(exchange), Models.BlockWriteRequest.class);
        if (req == null || req.file == null || req.file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "invalid request: missing file");
            return;
        }

        String modeHeader = exchange.getRequestHeaders().getFirst("X-Consistency-Mode");
        Models.ConsistencyMode mode = Models.ConsistencyMode.from(modeHeader);
        List<String> remainChain = parseChain(exchange.getRequestHeaders().getFirst("X-Replica-Chain"));

        ReentrantLock lock = blockLocks.computeIfAbsent(blockId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            writeLocal(blockId, req);
            if (!remainChain.isEmpty()) {
                if (mode == Models.ConsistencyMode.SYNC) {
                    forward(blockId, req, remainChain, mode);
                } else {
                    asyncForwardPool.submit(() -> {
                        try {
                            forward(blockId, req, remainChain, mode);
                        } catch (Exception e) {
                            System.err.println("async forward failed on " + nodeId + " block=" + blockId + ": " + e.getMessage());
                        }
                    });
                }
            }
        } finally {
            lock.unlock();
        }

        Models.BlockWriteAck ack = new Models.BlockWriteAck();
        ack.ok = true;
        ack.nodeId = nodeId;
        ack.blockId = blockId;
        ack.file = req.file;
        ack.index = req.index;
        ack.sizeBytes = req.sizeBytes;
        ack.mode = mode.name();
        ack.message = "block persisted";
        HttpUtil.sendJson(exchange, 200, ack);
    }

    private void handleBlockRead(HttpExchange exchange, String blockId) throws IOException {
        Map<String, String> q = HttpUtil.parseQuery(exchange.getRequestURI());
        String file = q.get("file");
        if (file == null || file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "missing file");
            return;
        }

        Path path = resolveBlockPath(file, blockId);
        Models.BlockReadResult result = new Models.BlockReadResult();
        result.nodeId = nodeId;
        result.blockId = blockId;
        result.file = file;
        result.exists = Files.exists(path);
        if (result.exists) {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            Models.BlockWriteRequest stored = Jsons.GSON.fromJson(raw, Models.BlockWriteRequest.class);
            result.index = stored.index;
            result.sizeBytes = stored.sizeBytes;
            result.payload = stored.payload;
        } else {
            result.index = -1;
            result.sizeBytes = 0;
            result.payload = "";
        }
        HttpUtil.sendJson(exchange, 200, result);
    }

    private void handleState(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        Models.NodeState state = new Models.NodeState();
        state.nodeId = nodeId;
        state.forwardDelayMs = forwardDelayMs;
        state.throttleBytesPerSec = throttleBytesPerSec;
        HttpUtil.sendJson(exchange, 200, state);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        HttpUtil.sendText(exchange, 200, "ok");
    }

    private void writeLocal(String blockId, Models.BlockWriteRequest req) throws IOException {
        Path path = resolveBlockPath(req.file, blockId);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, Jsons.GSON.toJson(req), StandardCharsets.UTF_8);
    }

    private void forward(String blockId,
                         Models.BlockWriteRequest req,
                         List<String> chain,
                         Models.ConsistencyMode mode) throws IOException {
        if (chain.isEmpty()) {
            return;
        }

        String nextUrl = chain.get(0);
        List<String> remain = chain.size() > 1 ? chain.subList(1, chain.size()) : List.of();

        String payload = Jsons.GSON.toJson(req);
        long transferDelay = calculateDelayMillis(payload.getBytes(StandardCharsets.UTF_8).length);
        if (transferDelay > 0L) {
            sleepQuietly(transferDelay);
        }

        String endpoint = nextUrl + "/api/v1/blocks/" + URLEncoder.encode(blockId, StandardCharsets.UTF_8);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Consistency-Mode", mode.name());
        headers.put("X-Replica-Chain", String.join(",", remain));

        HttpUtil.put(endpoint, payload, headers, 3000, 10000);
    }

    private long calculateDelayMillis(int bytes) {
        long byBandwidth = 0L;
        if (throttleBytesPerSec > 0L) {
            byBandwidth = (bytes * 1000L) / throttleBytesPerSec;
        }
        return forwardDelayMs + byBandwidth;
    }

    private Path resolveBlockPath(String file, String blockId) {
        String safe = file.replace("..", "_").replace('\\', '/');
        String safeBlockId = blockId.replace("..", "_").replace('/', '_').replace('\\', '_');
        return storageDir.resolve(safe + "__" + safeBlockId + ".json").normalize();
    }

    private static List<String> parseChain(String chainRaw) {
        if (chainRaw == null || chainRaw.isBlank()) {
            return List.of();
        }
        String[] parts = chainRaw.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        String nodeId = "dn1";
        int port = 9001;
        Path storageDir = Path.of("data/dn1");
        long forwardDelayMs = 1500;
        long throttleBytesPerSec = 1024;

        for (String arg : args) {
            if (arg.startsWith("--id=")) {
                nodeId = arg.substring("--id=".length());
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--storageDir=")) {
                storageDir = Path.of(arg.substring("--storageDir=".length()));
            } else if (arg.startsWith("--forwardDelayMs=")) {
                forwardDelayMs = Long.parseLong(arg.substring("--forwardDelayMs=".length()));
            } else if (arg.startsWith("--throttleBytesPerSec=")) {
                throttleBytesPerSec = Long.parseLong(arg.substring("--throttleBytesPerSec=".length()));
            }
        }

        new DataNodeServer(nodeId, port, storageDir, forwardDelayMs, throttleBytesPerSec).start();
    }
}
