package com.example.hdfs.client;

import com.example.hdfs.common.Constants;
import com.example.hdfs.rpc.AllocateBlockRequest;
import com.example.hdfs.rpc.AllocateBlockResponse;
import com.example.hdfs.rpc.AppendBlockRequest;
import com.example.hdfs.rpc.AppendBlockResponse;
import com.example.hdfs.rpc.BlockInfo;
import com.example.hdfs.rpc.CloseRequest;
import com.example.hdfs.rpc.DataNodeServiceGrpc;
import com.example.hdfs.rpc.FileMetadata;
import com.example.hdfs.rpc.NameNodeServiceGrpc;
import com.example.hdfs.rpc.OpenMode;
import com.example.hdfs.rpc.OpenRequest;
import com.example.hdfs.rpc.OpenResponse;
import com.example.hdfs.rpc.ReadBlockRequest;
import com.example.hdfs.rpc.ReadBlockResponse;
import com.example.hdfs.rpc.UpdateBlockRequest;
import com.example.hdfs.rpc.UpdateBlockResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleHdfsClient implements AutoCloseable {
    private final ManagedChannel nameNodeChannel;
    private final NameNodeServiceGrpc.NameNodeServiceBlockingStub nameNode;
    private final Map<String, DataNodeServiceGrpc.DataNodeServiceBlockingStub> dataNodeStubCache = new HashMap<>();
    private final Map<String, ManagedChannel> dataNodeChannelCache = new HashMap<>();

    private final Map<Integer, OpenFileSession> openFiles = new HashMap<>();
    private final AtomicInteger fdGenerator = new AtomicInteger(100);

    public SimpleHdfsClient(String nameNodeAddress) {
        String[] hp = splitAddress(nameNodeAddress);
        this.nameNodeChannel = ManagedChannelBuilder.forAddress(hp[0], Integer.parseInt(hp[1]))
                .usePlaintext()
                .build();
        this.nameNode = NameNodeServiceGrpc.newBlockingStub(nameNodeChannel);
    }

    public int open(String path, String modeText) {
        OpenMode mode = parseMode(modeText);
        OpenResponse res = nameNode.open(OpenRequest.newBuilder().setPath(path).setMode(mode).build());
        if (!res.getSuccess()) {
            return -1;
        }
        int fd = fdGenerator.incrementAndGet();
        openFiles.put(fd, new OpenFileSession(fd, path, mode, res.getMetadata()));
        return fd;
    }

    public boolean append(int fd, byte[] bytes) {
        OpenFileSession session = openFiles.get(fd);
        if (session == null || session.getMode() != OpenMode.WRITE) {
            return false;
        }

        int offset = 0;
        FileMetadata metadata = session.getMetadata();

        while (offset < bytes.length) {
            BlockInfo target = null;
            int currentSize = 0;

            if (metadata.getBlocksCount() > 0) {
                target = metadata.getBlocks(metadata.getBlocksCount() - 1);
                currentSize = target.getSize();
            }

            if (target == null || currentSize >= Constants.BLOCK_SIZE) {
                AllocateBlockResponse alloc = nameNode.allocateBlock(
                        AllocateBlockRequest.newBuilder().setPath(session.getPath()).build()
                );
                if (!alloc.getSuccess()) {
                    return false;
                }
                target = alloc.getBlock();
                currentSize = 0;
                metadata = metadata.toBuilder().addBlocks(target).build();
            }

            int writable = Math.min(Constants.BLOCK_SIZE - currentSize, bytes.length - offset);
            byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + writable);
            AppendBlockResponse appendRes = dataNode(target.getDatanodeAddress())
                    .appendBlock(AppendBlockRequest.newBuilder()
                            .setBlockId(target.getBlockId())
                            .setData(com.google.protobuf.ByteString.copyFrom(chunk))
                            .build());

            if (!appendRes.getSuccess()) {
                return false;
            }

            int newBlockSize = appendRes.getBlockSize();
            UpdateBlockResponse updateRes = nameNode.updateBlock(UpdateBlockRequest.newBuilder()
                    .setPath(session.getPath())
                    .setBlockId(target.getBlockId())
                    .setDatanodeId(target.getDatanodeId())
                    .setDatanodeAddress(target.getDatanodeAddress())
                    .setBlockSize(newBlockSize)
                    .build());
            if (!updateRes.getSuccess()) {
                return false;
            }

            metadata = updateBlockSizeInMetadata(metadata, target.getBlockId(), newBlockSize);
            offset += writable;
        }

        session.setMetadata(metadata);
        return true;
    }

    public byte[] read(int fd) {
        OpenFileSession session = openFiles.get(fd);
        if (session == null || session.getMode() != OpenMode.READ) {
            return null;
        }

        FileMetadata metadata = session.getMetadata();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            for (BlockInfo b : metadata.getBlocksList()) {
                ReadBlockResponse res = dataNode(b.getDatanodeAddress())
                        .readBlock(ReadBlockRequest.newBuilder().setBlockId(b.getBlockId()).build());
                if (!res.getSuccess()) {
                    return null;
                }
                bos.write(res.getData().toByteArray());
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    public boolean close(int fd) {
        OpenFileSession session = openFiles.get(fd);
        if (session == null) {
            return false;
        }
        boolean ok = nameNode.close(CloseRequest.newBuilder().setPath(session.getPath()).build()).getSuccess();
        openFiles.remove(fd);
        return ok;
    }

    private DataNodeServiceGrpc.DataNodeServiceBlockingStub dataNode(String address) {
        return dataNodeStubCache.computeIfAbsent(address, addr -> {
            String[] hp = splitAddress(addr);
            ManagedChannel ch = ManagedChannelBuilder.forAddress(hp[0], Integer.parseInt(hp[1]))
                    .usePlaintext()
                    .build();
            dataNodeChannelCache.put(addr, ch);
            return DataNodeServiceGrpc.newBlockingStub(ch);
        });
    }

    private static String[] splitAddress(String address) {
        String[] hp = address.split(":");
        if (hp.length != 2) {
            throw new IllegalArgumentException("Invalid address: " + address + ". expected host:port");
        }
        return hp;
    }

    private static OpenMode parseMode(String modeText) {
        return switch (modeText.toLowerCase()) {
            case "r" -> OpenMode.READ;
            case "w" -> OpenMode.WRITE;
            default -> throw new IllegalArgumentException("Unsupported mode: " + modeText + " (use r/w)");
        };
    }

    private FileMetadata updateBlockSizeInMetadata(FileMetadata md, String blockId, int newSize) {
        FileMetadata.Builder b = md.toBuilder().clearBlocks();
        long total = 0;
        for (BlockInfo bi : md.getBlocksList()) {
            if (bi.getBlockId().equals(blockId)) {
                bi = bi.toBuilder().setSize(newSize).build();
            }
            total += bi.getSize();
            b.addBlocks(bi);
        }
        b.setSize(total);
        return b.build();
    }

    @Override
    public void close() {
        for (ManagedChannel ch : dataNodeChannelCache.values()) {
            ch.shutdownNow();
        }
        nameNodeChannel.shutdownNow();
    }
}
