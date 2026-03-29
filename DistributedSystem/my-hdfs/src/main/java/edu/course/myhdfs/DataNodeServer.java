package edu.course.myhdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class DataNodeServer {
    private final String nodeId;
    private final int port;
    private final Path storageDir;
    private final String nextUrl;
    private final long forwardDelayMs;
    private final long throttleBytesPerSec;

    private final Map<String, Long> lastSeq = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    private final ExecutorService requestPool = Executors.newFixedThreadPool(12);
    private final ExecutorService asyncForwardPool = Executors.newFixedThreadPool(4);

    private HttpServer server;

    public DataNodeServer(String nodeId,
                          int port,
                          Path storageDir,
                          String nextUrl,
                          long forwardDelayMs,
                          long throttleBytesPerSec) {
        this.nodeId = nodeId;
        this.port = port;
        this.storageDir = storageDir;
        this.nextUrl = (nextUrl == null || nextUrl.isBlank()) ? null : nextUrl;
        this.forwardDelayMs = Math.max(0L, forwardDelayMs);
        this.throttleBytesPerSec = Math.max(0L, throttleBytesPerSec);
    }

    public void start() throws IOException {
        Files.createDirectories(storageDir);
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/write", this::handleWrite);
        server.createContext("/replicate", this::handleReplicate);
        server.createContext("/read", this::handleRead);
        server.createContext("/state", this::handleState);
        server.createContext("/health", this::handleHealth);
        server.setExecutor(requestPool);
        server.start();
        System.out.printf("DataNode %s started on :%d, next=%s, delayMs=%d, throttleBps=%d, dir=%s%n",
                nodeId, port, nextUrl, forwardDelayMs, throttleBytesPerSec, storageDir);
    }

    private void handleWrite(HttpExchange exchange) throws IOException {
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        handleWriteInternal(exchange, true);
    }

    private void handleReplicate(HttpExchange exchange) throws IOException {
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        handleWriteInternal(exchange, false);
    }

    private void handleWriteInternal(HttpExchange exchange, boolean fromClient) throws IOException {
        Map<String, String> q = HttpUtil.parseQuery(exchange.getRequestURI());
        String file = q.get("file");
        if (file == null || file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "missing file");
            return;
        }

        String body = HttpUtil.readBody(exchange);
        String modeHeader = exchange.getRequestHeaders().getFirst("X-Consistency-Mode");
        Models.ConsistencyMode mode = Models.ConsistencyMode.from(modeHeader);
        String seqHeader = exchange.getRequestHeaders().getFirst("X-Seq");
        long seq = seqHeader == null ? System.nanoTime() : Long.parseLong(seqHeader);
        String clientId = exchange.getRequestHeaders().getFirst("X-Client-Id");
        if (clientId == null || clientId.isBlank()) {
            clientId = "anonymous";
        }

        ReentrantLock lock = fileLocks.computeIfAbsent(file, ignored -> new ReentrantLock());
        lock.lock();
        try {
            writeLocal(file, body, seq);
            if (nextUrl != null) {
                if (mode == Models.ConsistencyMode.SYNC) {
                    forwardToNext(file, body, mode, clientId, seq);
                } else {
                    String finalClientId = clientId;
                    asyncForwardPool.submit(() -> {
                        try {
                            forwardToNext(file, body, mode, finalClientId, seq);
                        } catch (Exception e) {
                            System.err.println("Async forward failed on " + nodeId + ": " + e.getMessage());
                        }
                    });
                }
            }
        } finally {
            lock.unlock();
        }

        Models.WriteAck ack = new Models.WriteAck();
        ack.ok = true;
        ack.nodeId = nodeId;
        ack.file = file;
        ack.mode = mode.name();
        ack.seq = seq;
        ack.message = fromClient
                ? "accepted by primary node"
                : "replicated";

        HttpUtil.sendJson(exchange, 200, ack);
    }

    private void handleRead(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        Map<String, String> q = HttpUtil.parseQuery(exchange.getRequestURI());
        String file = q.get("file");
        if (file == null || file.isBlank()) {
            HttpUtil.sendText(exchange, 400, "missing file");
            return;
        }

        Path path = resolveFile(file);
        Models.ReadResult result = new Models.ReadResult();
        result.nodeId = nodeId;
        result.file = file;
        result.exists = Files.exists(path);
        result.lastSeq = lastSeq.getOrDefault(file, -1L);
        result.value = result.exists ? Files.readString(path, StandardCharsets.UTF_8) : "";

        HttpUtil.sendJson(exchange, 200, result);
    }

    private void handleState(HttpExchange exchange) throws IOException {
        Models.NodeState state = new Models.NodeState();
        state.nodeId = nodeId;
        state.nextUrl = nextUrl;
        state.forwardDelayMs = forwardDelayMs;
        state.throttleBytesPerSec = throttleBytesPerSec;
        HttpUtil.sendJson(exchange, 200, state);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        HttpUtil.sendText(exchange, 200, "ok");
    }

    private void writeLocal(String file, String value, long seq) throws IOException {
        Path path = resolveFile(file);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, value, StandardCharsets.UTF_8);
        lastSeq.put(file, seq);
    }

    private void forwardToNext(String file,
                               String body,
                               Models.ConsistencyMode mode,
                               String clientId,
                               long seq) throws IOException {
        long transferDelay = calculateDelayMillis(body.getBytes(StandardCharsets.UTF_8).length);
        if (transferDelay > 0L) {
            sleepQuietly(transferDelay);
        }

        String encodedFile = URLEncoder.encode(file, StandardCharsets.UTF_8);
        String endpoint = nextUrl + "/replicate?file=" + encodedFile;

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Consistency-Mode", mode.name());
        headers.put("X-Client-Id", clientId);
        headers.put("X-Seq", String.valueOf(seq));

        HttpUtil.put(endpoint, body, headers, 3000, 10000);
    }

    private long calculateDelayMillis(int bytes) {
        long byBandwidth = 0L;
        if (throttleBytesPerSec > 0L) {
            byBandwidth = (bytes * 1000L) / throttleBytesPerSec;
        }
        return forwardDelayMs + byBandwidth;
    }

    private Path resolveFile(String file) {
        String safe = file.replace("..", "_").replace('\\', '/');
        return storageDir.resolve(safe + ".txt").normalize();
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
        String nextUrl = "http://127.0.0.1:9002";
        long forwardDelayMs = 1500;
        long throttleBytesPerSec = 1024;

        for (String arg : args) {
            if (arg.startsWith("--id=")) {
                nodeId = arg.substring("--id=".length());
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--storageDir=")) {
                storageDir = Path.of(arg.substring("--storageDir=".length()));
            } else if (arg.startsWith("--nextUrl=")) {
                String v = arg.substring("--nextUrl=".length());
                nextUrl = ("none".equalsIgnoreCase(v) || v.isBlank()) ? null : v;
            } else if (arg.startsWith("--forwardDelayMs=")) {
                forwardDelayMs = Long.parseLong(arg.substring("--forwardDelayMs=".length()));
            } else if (arg.startsWith("--throttleBytesPerSec=")) {
                throttleBytesPerSec = Long.parseLong(arg.substring("--throttleBytesPerSec=".length()));
            }
        }

        new DataNodeServer(nodeId, port, storageDir, nextUrl, forwardDelayMs, throttleBytesPerSec).start();
    }
}
