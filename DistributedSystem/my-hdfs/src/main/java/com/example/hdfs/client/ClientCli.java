package com.example.hdfs.client;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientCli {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: ClientCli <namenodeHost:port>");
            return;
        }

        try (SimpleHdfsClient client = new SimpleHdfsClient(args[0]); Scanner scanner = new Scanner(System.in)) {
            System.out.println("Commands:");
            System.out.println("  open <path> <r|w>");
            System.out.println("  append <fd> <text>");
            System.out.println("  read <fd>");
            System.out.println("  close <fd>");
            System.out.println("  check <path>");
            System.out.println("  exit");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }

                String[] parts = line.split(" ", 3);
                String cmd = parts[0].toLowerCase();

                try {
                    switch (cmd) {
                        case "open" -> {
                            if (parts.length < 3) {
                                System.out.println("usage: open <path> <r|w>");
                                continue;
                            }
                            int fd = client.open(parts[1], parts[2]);
                            System.out.println(fd >= 0 ? ("fd=" + fd) : "open failed");
                        }
                        case "append" -> {
                            if (parts.length < 3) {
                                System.out.println("usage: append <fd> <text>");
                                continue;
                            }
                            int fd = Integer.parseInt(parts[1]);
                            boolean ok = client.append(fd, parts[2].getBytes(StandardCharsets.UTF_8));
                            System.out.println(ok ? "append ok" : ("append failed: " + client.getLastError()));
                        }
                        case "read" -> {
                            if (parts.length < 2) {
                                System.out.println("usage: read <fd>");
                                continue;
                            }
                            int fd = Integer.parseInt(parts[1]);
                            byte[] data = client.read(fd);
                            System.out.println(data == null ? ("read failed: " + client.getLastError()) : new String(data, StandardCharsets.UTF_8));
                        }
                        case "close" -> {
                            if (parts.length < 2) {
                                System.out.println("usage: close <fd>");
                                continue;
                            }
                            int fd = Integer.parseInt(parts[1]);
                            boolean ok = client.close(fd);
                            System.out.println(ok ? "close ok" : ("close failed: " + client.getLastError()));
                        }
                        case "check" -> {
                            if (parts.length < 2) {
                                System.out.println("usage: check <path>");
                                continue;
                            }
                            ConsistencyReport report = client.inspectFileConsistency(parts[1]);
                            System.out.println("check " + (report.success() ? "ok" : "failed")
                                    + ", severity=" + report.severity() + ", " + report.summary());
                            report.details().forEach(System.out::println);
                        }
                        default -> System.out.println("Unknown command");
                    }
                } catch (Exception e) {
                    System.out.println("error: " + e.getMessage());
                }
            }
        }
    }
}
