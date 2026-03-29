package edu.course.myhdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class NameNodeServer {
    private final int port;
    private final List<Models.ReplicaInfo> replicas;
    private HttpServer server;

    public NameNodeServer(int port, List<Models.ReplicaInfo> replicas) {
        this.port = port;
        this.replicas = List.copyOf(replicas);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/allocate", this::handleAllocate);
        server.createContext("/replicas", this::handleReplicas);
        server.createContext("/health", this::handleHealth);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("NameNode started on :" + port + ", replicas=" + Jsons.GSON.toJson(replicas));
    }

    private void handleAllocate(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        Map<String, String> q = HttpUtil.parseQuery(exchange.getRequestURI());
        String file = q.getOrDefault("file", "unknown");

        Models.MetadataResponse resp = new Models.MetadataResponse();
        resp.file = file;
        resp.replicas = replicas;
        resp.primaryId = replicas.isEmpty() ? "" : replicas.get(0).id;
        resp.primaryUrl = replicas.isEmpty() ? "" : replicas.get(0).baseUrl;

        HttpUtil.sendJson(exchange, 200, resp);
    }

    private void handleReplicas(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        HttpUtil.sendJson(exchange, 200, replicas);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        HttpUtil.sendText(exchange, 200, "ok");
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        String replicasArg = "dn1=http://127.0.0.1:9001,dn2=http://127.0.0.1:9002,dn3=http://127.0.0.1:9003";

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--replicas=")) {
                replicasArg = arg.substring("--replicas=".length());
            }
        }

        List<Models.ReplicaInfo> replicas = parseReplicas(replicasArg);
        if (replicas.isEmpty()) {
            throw new IllegalArgumentException("No replicas configured");
        }

        new NameNodeServer(port, replicas).start();
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
