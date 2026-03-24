package com.example.hdfs.namenode;

import com.example.hdfs.common.DataNodeEndpoint;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NameNodeServer {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: NameNodeServer <port> <fsImagePath> <datanodeId=host:port[,datanodeId=host:port...]> ");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Path fsImage = Path.of(args[1]);
        List<DataNodeEndpoint> endpoints = parseEndpoints(args[2]);

        MetadataManager manager = new MetadataManager(new FsImageStore(fsImage), endpoints);
        Server server = ServerBuilder.forPort(port)
                .addService(new NameNodeServiceImpl(manager))
                .build()
                .start();

        System.out.println("NameNode started on port " + port + ", fsImage=" + fsImage);
        server.awaitTermination();
    }

    private static List<DataNodeEndpoint> parseEndpoints(String raw) {
        List<DataNodeEndpoint> list = new ArrayList<>();
        String[] entries = raw.split(",");
        for (String e : entries) {
            String[] kv = e.split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("Invalid DataNode endpoint format: " + e);
            }
            list.add(new DataNodeEndpoint(kv[0].trim(), kv[1].trim()));
        }
        return list;
    }
}
