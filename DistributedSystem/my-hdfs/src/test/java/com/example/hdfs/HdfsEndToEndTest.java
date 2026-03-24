package com.example.hdfs;

import com.example.hdfs.client.SimpleHdfsClient;
import com.example.hdfs.common.DataNodeEndpoint;
import com.example.hdfs.datanode.DataNodeServiceImpl;
import com.example.hdfs.datanode.DataNodeStorage;
import com.example.hdfs.namenode.FsImage;
import com.example.hdfs.namenode.FsImageStore;
import com.example.hdfs.namenode.MetadataManager;
import com.example.hdfs.namenode.NameNodeServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HdfsEndToEndTest {

    private Server dn1Server;
    private Server dn2Server;
    private Server nnServer;

    private int dn1Port;
    private int dn2Port;
    private int nnPort;

    private Path fsImagePath;

    @BeforeAll
    void startCluster(@TempDir Path tempDir) throws Exception {
        dn1Port = findFreePort();
        dn2Port = findFreePort();
        nnPort = findFreePort();

        Path dn1Dir = tempDir.resolve("dn1");
        Path dn2Dir = tempDir.resolve("dn2");
        fsImagePath = tempDir.resolve("fsimage.json");

        dn1Server = ServerBuilder.forPort(dn1Port)
                .addService(new DataNodeServiceImpl(new DataNodeStorage("dn1", dn1Dir)))
                .build()
                .start();

        dn2Server = ServerBuilder.forPort(dn2Port)
                .addService(new DataNodeServiceImpl(new DataNodeStorage("dn2", dn2Dir)))
                .build()
                .start();

        List<DataNodeEndpoint> endpoints = List.of(
                new DataNodeEndpoint("dn1", "127.0.0.1:" + dn1Port),
                new DataNodeEndpoint("dn2", "127.0.0.1:" + dn2Port)
        );

        MetadataManager manager = new MetadataManager(new FsImageStore(fsImagePath), endpoints);
        nnServer = ServerBuilder.forPort(nnPort)
                .addService(new NameNodeServiceImpl(manager))
                .build()
                .start();
    }

    @AfterAll
    void stopCluster() {
        if (nnServer != null) {
            nnServer.shutdownNow();
        }
        if (dn1Server != null) {
            dn1Server.shutdownNow();
        }
        if (dn2Server != null) {
            dn2Server.shutdownNow();
        }
    }

    @Test
    void shouldEnforceSingleWriterAndAllowRetryAfterClose() {
        String nameNodeAddress = "127.0.0.1:" + nnPort;

        try (SimpleHdfsClient c1 = new SimpleHdfsClient(nameNodeAddress);
             SimpleHdfsClient c2 = new SimpleHdfsClient(nameNodeAddress)) {

            int fd1 = c1.open("/lock.txt", "w");
            assertTrue(fd1 > 0);

            int fd2 = c2.open("/lock.txt", "w");
            assertEquals(-1, fd2);

            assertTrue(c1.append(fd1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertTrue(c1.close(fd1));

            int fd2Retry = c2.open("/lock.txt", "w");
            assertTrue(fd2Retry > 0);
            assertTrue(c2.close(fd2Retry));
        }
    }

    @Test
    void shouldAppendAcrossBlocksAndReadBackExactly() {
        String nameNodeAddress = "127.0.0.1:" + nnPort;

        byte[] payload = new byte[5000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + (i % 26));
        }

        try (SimpleHdfsClient client = new SimpleHdfsClient(nameNodeAddress)) {
            int fdw = client.open("/big.bin", "w");
            assertTrue(fdw > 0);
            assertTrue(client.append(fdw, payload));
            assertTrue(client.close(fdw));

            int fdr = client.open("/big.bin", "r");
            assertTrue(fdr > 0);
            byte[] data = client.read(fdr);
            assertNotNull(data);
            assertEquals(payload.length, data.length);
            assertArrayEquals(payload, data);
            assertTrue(client.close(fdr));
        }
    }

    @Test
    void shouldRespectReadWriteModeRules() {
        String nameNodeAddress = "127.0.0.1:" + nnPort;

        try (SimpleHdfsClient client = new SimpleHdfsClient(nameNodeAddress)) {
            int fdw = client.open("/mode.txt", "w");
            assertTrue(fdw > 0);
            assertTrue(client.append(fdw, "abc".getBytes(StandardCharsets.UTF_8)));
            assertNull(client.read(fdw));
            assertTrue(client.close(fdw));

            int fdr = client.open("/mode.txt", "r");
            assertTrue(fdr > 0);
            assertFalse(client.append(fdr, "x".getBytes(StandardCharsets.UTF_8)));
            assertTrue(client.close(fdr));
        }
    }

    @Test
    void shouldPersistFsImageMetadataAfterClose() {
        String nameNodeAddress = "127.0.0.1:" + nnPort;

        try (SimpleHdfsClient client = new SimpleHdfsClient(nameNodeAddress)) {
            int fd = client.open("/persist.txt", "w");
            assertTrue(fd > 0);
            assertTrue(client.append(fd, "persist-check".getBytes(StandardCharsets.UTF_8)));
            assertTrue(client.close(fd));
        }

        FsImage image = new FsImageStore(fsImagePath).load();
        assertNotNull(image.getFiles().get("/persist.txt"));
        assertTrue(image.getFiles().get("/persist.txt").getSize() > 0);
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
