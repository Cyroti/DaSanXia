package com.example.hdfs.datanode;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.nio.file.Path;

public class DataNodeServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: DataNodeServer <dataNodeId> <port> <storageDir> [artificialDelayMs]");
            return;
        }

        String dataNodeId = args[0];
        int port = Integer.parseInt(args[1]);
        Path storageDir = Path.of(args[2]);
        long delayMs = args.length >= 4 ? Long.parseLong(args[3]) : 0L;

        DataNodeStorage storage = new DataNodeStorage(dataNodeId, storageDir);

        Server server = ServerBuilder.forPort(port)
            .addService(new DataNodeServiceImpl(storage, delayMs))
                .build()
                .start();

        System.out.println("DataNode " + dataNodeId + " started on port " + port + ", dir=" + storageDir + ", delayMs=" + delayMs);
        server.awaitTermination();
    }
}
