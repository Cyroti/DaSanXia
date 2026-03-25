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
import com.example.hdfs.rpc.ReplicaInfo;
import com.example.hdfs.rpc.UpdateBlockRequest;
import com.example.hdfs.rpc.UpdateBlockResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

public class SimpleHdfsClient implements AutoCloseable {
    private final ManagedChannel nameNodeChannel;
    private final NameNodeServiceGrpc.NameNodeServiceBlockingStub nameNode;
    private final Map<String, DataNodeServiceGrpc.DataNodeServiceBlockingStub> dataNodeStubCache = new HashMap<>();
    private final Map<String, ManagedChannel> dataNodeChannelCache = new HashMap<>();

    private final Map<Integer, OpenFileSession> openFiles = new HashMap<>();
    private final AtomicInteger fdGenerator = new AtomicInteger(100);
    private volatile String lastError = "";
    private volatile String writeAttemptDetail = "";

    public SimpleHdfsClient(String nameNodeAddress) {
        String[] hp = splitAddress(nameNodeAddress);
        this.nameNodeChannel = ManagedChannelBuilder.forAddress(hp[0], Integer.parseInt(hp[1]))
                .usePlaintext()
                .build();
        this.nameNode = NameNodeServiceGrpc.newBlockingStub(nameNodeChannel);
    }

    public int open(String path, String modeText) {
        lastError = "";
        OpenMode mode = parseMode(modeText);
        OpenResponse res = nameNode.open(OpenRequest.newBuilder().setPath(path).setMode(mode).build());
        if (!res.getSuccess()) {
            lastError = "open failed: " + res.getMessage();
            return -1;
        }
        int fd = fdGenerator.incrementAndGet();
        openFiles.put(fd, new OpenFileSession(fd, path, mode, res.getMetadata()));
        return fd;
    }

    public boolean append(int fd, byte[] bytes) {
        lastError = "";
        writeAttemptDetail = "";
        OpenFileSession session = openFiles.get(fd);
        if (session == null || session.getMode() != OpenMode.WRITE) {
            lastError = "append failed: invalid fd or non-write mode";
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
                    lastError = "append failed: allocate block failed, reason=" + alloc.getMessage();
                    return false;
                }
                target = alloc.getBlock();
                currentSize = 0;
                metadata = metadata.toBuilder().addBlocks(target).build();
            }

            int writable = Math.min(Constants.BLOCK_SIZE - currentSize, bytes.length - offset);
            byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + writable);

            List<ReplicaWriteState> successReplicas = appendThroughPipeline(target, chunk);
            if (successReplicas.size() < Constants.REQUIRED_REPLICA_ACKS) {
                lastError = "append failed: replication not complete, success=" + successReplicas.size()
                        + "/" + target.getReplicasCount()
                        + (writeAttemptDetail.isBlank() ? "" : ", detail=" + writeAttemptDetail);
                return false;
            }

            for (ReplicaWriteState state : successReplicas) {
                UpdateBlockResponse updateRes = nameNode.updateBlock(UpdateBlockRequest.newBuilder()
                        .setPath(session.getPath())
                        .setBlockId(target.getBlockId())
                        .setReplica(ReplicaInfo.newBuilder()
                                .setDatanodeId(state.datanodeId)
                                .setDatanodeAddress(state.address)
                                .setSize(state.blockSize)
                                .setChecksum(state.checksum)
                                .setUpdateTime(System.currentTimeMillis())
                                .build())
                        .build());
                if (!updateRes.getSuccess()) {
                    lastError = "append failed: update block metadata failed, reason=" + updateRes.getMessage();
                    return false;
                }
            }

            metadata = updateBlockReplicaInMetadata(metadata, target.getBlockId(), successReplicas);
            offset += writable;
        }

        session.setMetadata(metadata);
        return true;
    }

    public byte[] read(int fd) {
        lastError = "";
        OpenFileSession session = openFiles.get(fd);
        if (session == null || session.getMode() != OpenMode.READ) {
            lastError = "read failed: invalid fd or non-read mode";
            return null;
        }

        FileMetadata metadata = session.getMetadata();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            for (BlockInfo b : metadata.getBlocksList()) {
                BlockReadResolution resolved = resolveBlockRead(b);
                if (resolved == null || resolved.data == null) {
                    lastError = "read failed: block " + b.getBlockId() + " has no readable replicas";
                    return null;
                }
                bos.write(resolved.data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    public boolean close(int fd) {
        lastError = "";
        OpenFileSession session = openFiles.get(fd);
        if (session == null) {
            lastError = "close failed: invalid fd";
            return false;
        }
        boolean ok = nameNode.close(CloseRequest.newBuilder().setPath(session.getPath()).build()).getSuccess();
        openFiles.remove(fd);
        if (!ok) {
            lastError = "close failed: namenode rejected close";
        }
        return ok;
    }

    public String getLastError() {
        return lastError == null ? "" : lastError;
    }

    public ConsistencyReport inspectFileConsistency(String path) {
        OpenResponse opened = nameNode.open(OpenRequest.newBuilder().setPath(path).setMode(OpenMode.READ).build());
        if (!opened.getSuccess()) {
            return ConsistencyReport.failed(path, "open failed: " + opened.getMessage());
        }
        try {
            return inspectMetadata(path, opened.getMetadata());
        } finally {
            nameNode.close(CloseRequest.newBuilder().setPath(path).build());
        }
    }

    private ConsistencyReport inspectMetadata(String path, FileMetadata metadata) {
        int inconsistentBlocks = 0;
        int totalBlocks = metadata.getBlocksCount();
        List<String> details = new ArrayList<>();
        ConsistencySeverity worst = ConsistencySeverity.CONSISTENT;

        for (BlockInfo block : metadata.getBlocksList()) {
            BlockReadResolution resolution = resolveBlockRead(block);
            if (resolution == null) {
                inconsistentBlocks++;
                worst = maxSeverity(worst, ConsistencySeverity.CRITICAL);
                details.add("block=" + block.getBlockId() + " all replicas unreadable");
                continue;
            }
            if (resolution.readableReplicas < Constants.REQUIRED_REPLICA_ACKS) {
                inconsistentBlocks++;
                worst = maxSeverity(worst, ConsistencySeverity.CRITICAL);
                details.add("block=" + block.getBlockId() + " readableReplicas=" + resolution.readableReplicas);
                continue;
            }

            if (resolution.majorityVotes < block.getReplicasCount()) {
                inconsistentBlocks++;
                if (resolution.majorityVotes >= Constants.REQUIRED_REPLICA_ACKS - 1) {
                    worst = maxSeverity(worst, ConsistencySeverity.MINOR);
                    details.add("block=" + block.getBlockId() + " has stale replicas");
                } else {
                    worst = maxSeverity(worst, ConsistencySeverity.MAJOR);
                    details.add("block=" + block.getBlockId() + " no strong majority");
                }
            }
        }

        String summary = "blocks=" + totalBlocks + ", inconsistentBlocks=" + inconsistentBlocks;
        return new ConsistencyReport(path, true, worst, summary, details);
    }

    private BlockReadResolution resolveBlockRead(BlockInfo block) {
        Map<String, List<byte[]>> byChecksum = new LinkedHashMap<>();
        int readable = 0;

        for (ReplicaInfo replica : block.getReplicasList()) {
            byte[] data = tryReadReplica(block.getBlockId(), replica);
            if (data == null) {
                continue;
            }
            readable++;
            String digest = checksum(data);
            byChecksum.computeIfAbsent(digest, ignored -> new ArrayList<>()).add(data);
        }

        if (byChecksum.isEmpty()) {
            return null;
        }

        String winner = null;
        int votes = 0;
        byte[] canonical = null;
        for (Map.Entry<String, List<byte[]>> e : byChecksum.entrySet()) {
            int v = e.getValue().size();
            byte[] sample = e.getValue().get(0);
            if (v > votes || (v == votes && sample.length > (canonical == null ? -1 : canonical.length))) {
                winner = e.getKey();
                votes = v;
                canonical = sample;
            }
        }

        return new BlockReadResolution(canonical, winner, votes, readable);
    }

    private byte[] tryReadReplica(String blockId, ReplicaInfo replica) {
        try {
            ReadBlockResponse res = dataNode(replica.getDatanodeAddress())
                    .withDeadlineAfter(Constants.RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readBlock(ReadBlockRequest.newBuilder().setBlockId(blockId).build());
            if (!res.getSuccess()) {
                return null;
            }
            return res.getData().toByteArray();
        } catch (StatusRuntimeException e) {
            return null;
        }
    }

    private List<ReplicaWriteState> appendThroughPipeline(BlockInfo block, byte[] chunk) {
        List<ReplicaWriteState> success = new ArrayList<>();
        if (block.getReplicasCount() == 0) {
            writeAttemptDetail = "pipeline is empty";
            return success;
        }

        List<ReplicaInfo> pipeline = new ArrayList<>(block.getReplicasList());
        pipeline.sort(Comparator.comparing(ReplicaInfo::getDatanodeId));
        ReplicaInfo head = pipeline.get(0);
        try {
            AppendBlockResponse appendRes = dataNode(head.getDatanodeAddress())
                    .withDeadlineAfter(Constants.RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .appendBlock(AppendBlockRequest.newBuilder()
                            .setBlockId(block.getBlockId())
                            .setData(com.google.protobuf.ByteString.copyFrom(chunk))
                            .addAllPipeline(pipeline)
                            .setPipelineIndex(0)
                            .setReplicated(false)
                            .build());

            if (!appendRes.getSuccess()) {
                writeAttemptDetail = "head rejected: " + appendRes.getMessage();
                return success;
            }

            for (ReplicaInfo ack : appendRes.getAckedReplicasList()) {
                success.add(new ReplicaWriteState(
                        ack.getDatanodeId(),
                        ack.getDatanodeAddress(),
                        ack.getSize(),
                        ack.getChecksum()
                ));
            }
            writeAttemptDetail = "head message=" + appendRes.getMessage() + ", acked=" + appendRes.getAckedReplicasCount();
        } catch (StatusRuntimeException ignored) {
            // 下游慢节点超时时链路可能只返回部分 ACK，用于观测提交后不一致。
            writeAttemptDetail = "head rpc error: " + ignored.getStatus();
        }

        return success;
    }

    private FileMetadata updateBlockReplicaInMetadata(FileMetadata md, String blockId, List<ReplicaWriteState> states) {
        Map<String, ReplicaWriteState> byNodeId = new HashMap<>();
        for (ReplicaWriteState s : states) {
            byNodeId.put(s.datanodeId, s);
        }

        FileMetadata.Builder b = md.toBuilder().clearBlocks();
        long total = 0;

        for (BlockInfo bi : md.getBlocksList()) {
            BlockInfo.Builder blockBuilder = bi.toBuilder().clearReplicas();
            for (ReplicaInfo ri : bi.getReplicasList()) {
                ReplicaWriteState state = byNodeId.get(ri.getDatanodeId());
                if (bi.getBlockId().equals(blockId) && state != null) {
                    ri = ri.toBuilder()
                            .setSize(state.blockSize)
                            .setChecksum(state.checksum)
                            .setUpdateTime(System.currentTimeMillis())
                            .build();
                }
                blockBuilder.addReplicas(ri);
            }

            if (bi.getBlockId().equals(blockId)) {
                blockBuilder.setSize(resolveCanonicalSize(blockBuilder.getReplicasList()));
            }

            BlockInfo updated = blockBuilder.build();
            total += updated.getSize();
            b.addBlocks(updated);
        }

        b.setSize(total);
        return b.build();
    }

    private int resolveCanonicalSize(List<ReplicaInfo> replicas) {
        Map<String, Integer> votes = new HashMap<>();
        for (ReplicaInfo r : replicas) {
            String key = r.getSize() + "#" + r.getChecksum();
            votes.put(key, votes.getOrDefault(key, 0) + 1);
        }
        String winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("0#");
        int idx = winner.indexOf('#');
        return Integer.parseInt(winner.substring(0, idx));
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

    private String checksum(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return Long.toHexString(crc32.getValue());
    }

    private ConsistencySeverity maxSeverity(ConsistencySeverity a, ConsistencySeverity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    @Override
    public void close() {
        for (ManagedChannel ch : dataNodeChannelCache.values()) {
            ch.shutdownNow();
        }
        nameNodeChannel.shutdownNow();
    }

    private record ReplicaWriteState(String datanodeId, String address, int blockSize, String checksum) {
    }

    private record BlockReadResolution(byte[] data, String checksum, int majorityVotes, int readableReplicas) {
    }
}
