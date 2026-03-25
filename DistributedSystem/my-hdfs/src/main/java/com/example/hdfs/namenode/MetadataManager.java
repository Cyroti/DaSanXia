package com.example.hdfs.namenode;

import com.example.hdfs.common.Constants;
import com.example.hdfs.common.DataNodeEndpoint;
import com.example.hdfs.rpc.BlockInfo;
import com.example.hdfs.rpc.FileMetadata;
import com.example.hdfs.rpc.OpenMode;
import com.example.hdfs.rpc.ReplicaInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MetadataManager {
    private final FsImageStore store;
    private final FsImage fsImage;
    private final List<DataNodeEndpoint> dataNodes;
    private final Map<String, Integer> readOpenCount = new HashMap<>();
    private final Map<String, Boolean> writeOpen = new HashMap<>();
    private final AtomicInteger dataNodeCursor = new AtomicInteger(0);

    public MetadataManager(FsImageStore store, List<DataNodeEndpoint> dataNodes) {
        if (dataNodes == null || dataNodes.isEmpty()) {
            throw new IllegalArgumentException("At least one DataNode endpoint is required");
        }
        if (dataNodes.size() < Constants.REQUIRED_REPLICA_ACKS) {
            throw new IllegalArgumentException("At least " + Constants.REQUIRED_REPLICA_ACKS + " DataNodes are required");
        }
        this.store = store;
        this.fsImage = store.load();
        this.dataNodes = new ArrayList<>(dataNodes);
    }

    public synchronized OpenResult open(String path, OpenMode mode) {
        if (path == null || path.isBlank()) {
            return OpenResult.failed("path is empty");
        }
        if (mode != OpenMode.READ && mode != OpenMode.WRITE) {
            return OpenResult.failed("unsupported open mode");
        }

        long now = System.currentTimeMillis();
        FileRecord file = fsImage.getFiles().get(path);
        if (file == null) {
            file = newFile(path, now);
            fsImage.getFiles().put(path, file);
            store.save(fsImage);
        }

        if (mode == OpenMode.WRITE) {
            if (Boolean.TRUE.equals(writeOpen.get(path))) {
                return OpenResult.failed("file is already opened for writing");
            }
            writeOpen.put(path, true);
        } else {
            readOpenCount.put(path, readOpenCount.getOrDefault(path, 0) + 1);
            file.setAccessTime(now);
            store.save(fsImage);
        }

        return OpenResult.success(toProto(file));
    }

    public synchronized boolean close(String path) {
        FileRecord file = fsImage.getFiles().get(path);
        if (file == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        file.setModifyTime(now);

        if (Boolean.TRUE.equals(writeOpen.get(path))) {
            writeOpen.remove(path);
        } else {
            int c = readOpenCount.getOrDefault(path, 0);
            if (c > 0) {
                if (c == 1) {
                    readOpenCount.remove(path);
                } else {
                    readOpenCount.put(path, c - 1);
                }
            }
        }
        store.save(fsImage);
        return true;
    }

    public synchronized BlockRecord allocateBlock(String path) {
        FileRecord file = fsImage.getFiles().get(path);
        if (file == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(writeOpen.get(path))) {
            return null;
        }

        List<DataNodeEndpoint> replicaEndpoints = pickReplicaDataNodes();
        BlockRecord block = new BlockRecord();
        block.setBlockId(UUID.randomUUID().toString());
        block.setSize(0);

        long now = System.currentTimeMillis();
        for (DataNodeEndpoint ep : replicaEndpoints) {
            ReplicaRecord r = new ReplicaRecord();
            r.setDataNodeId(ep.id());
            r.setDataNodeAddress(ep.address());
            r.setSize(0);
            r.setChecksum("");
            r.setUpdateTime(now);
            block.getReplicas().add(r);
        }

        file.getBlocks().add(block);
        file.setModifyTime(now);
        store.save(fsImage);
        return block;
    }

    public synchronized boolean updateBlockReplica(String path, String blockId, ReplicaInfo replicaInfo) {
        FileRecord file = fsImage.getFiles().get(path);
        if (file == null) {
            return false;
        }
        if (replicaInfo.getSize() < 0 || replicaInfo.getSize() > Constants.BLOCK_SIZE) {
            return false;
        }

        BlockRecord target = null;
        for (BlockRecord b : file.getBlocks()) {
            if (b.getBlockId().equals(blockId)) {
                target = b;
                break;
            }
        }
        if (target == null) {
            return false;
        }

        ReplicaRecord rr = findReplica(target, replicaInfo.getDatanodeId());
        if (rr == null) {
            rr = new ReplicaRecord();
            rr.setDataNodeId(replicaInfo.getDatanodeId());
            rr.setDataNodeAddress(replicaInfo.getDatanodeAddress());
            target.getReplicas().add(rr);
        }
        rr.setSize(replicaInfo.getSize());
        rr.setChecksum(replicaInfo.getChecksum());
        rr.setUpdateTime(replicaInfo.getUpdateTime());

        target.setSize(resolveCanonicalReplicaSize(target));

        long total = file.getBlocks().stream().map(BlockRecord::getSize).reduce(0, Integer::sum);
        file.setSize(total);
        long now = System.currentTimeMillis();
        file.setModifyTime(now);
        file.setAccessTime(now);
        store.save(fsImage);
        return true;
    }

    private ReplicaRecord findReplica(BlockRecord block, String dataNodeId) {
        for (ReplicaRecord r : block.getReplicas()) {
            if (r.getDataNodeId().equals(dataNodeId)) {
                return r;
            }
        }
        return null;
    }

    private int resolveCanonicalReplicaSize(BlockRecord block) {
        if (block.getReplicas().isEmpty()) {
            return 0;
        }

        Map<String, Integer> votes = new HashMap<>();
        for (ReplicaRecord r : block.getReplicas()) {
            String key = r.getSize() + "#" + (r.getChecksum() == null ? "" : r.getChecksum());
            votes.put(key, votes.getOrDefault(key, 0) + 1);
        }

        String winner = votes.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(e -> parseSizeFromVoteKey(e.getKey())))
                .map(Map.Entry::getKey)
                .orElse("0#");

        return parseSizeFromVoteKey(winner);
    }

    private int parseSizeFromVoteKey(String key) {
        int idx = key.indexOf('#');
        if (idx < 0) {
            return 0;
        }
        return Integer.parseInt(key.substring(0, idx));
    }

    private List<DataNodeEndpoint> pickReplicaDataNodes() {
        int replicaCount = Math.min(Constants.REPLICATION_FACTOR, dataNodes.size());
        int start = Math.floorMod(dataNodeCursor.getAndIncrement(), dataNodes.size());
        List<DataNodeEndpoint> picked = new ArrayList<>(replicaCount);
        for (int i = 0; i < replicaCount; i++) {
            picked.add(dataNodes.get((start + i) % dataNodes.size()));
        }
        return picked;
    }

    private FileRecord newFile(String path, long now) {
        FileRecord f = new FileRecord();
        f.setPath(path);
        f.setSize(0);
        f.setCreateTime(now);
        f.setModifyTime(now);
        f.setAccessTime(now);
        f.setBlocks(new ArrayList<>());
        return f;
    }

    private FileMetadata toProto(FileRecord fr) {
        List<BlockRecord> blocks = fr.getBlocks();

        FileMetadata.Builder builder = FileMetadata.newBuilder()
                .setPath(fr.getPath())
                .setSize(fr.getSize())
                .setCreateTime(fr.getCreateTime())
                .setModifyTime(fr.getModifyTime())
                .setAccessTime(fr.getAccessTime());

        for (BlockRecord b : blocks) {
            BlockInfo.Builder blockBuilder = BlockInfo.newBuilder()
                    .setBlockId(b.getBlockId())
                    .setSize(b.getSize());

            for (ReplicaRecord r : b.getReplicas()) {
                blockBuilder.addReplicas(ReplicaInfo.newBuilder()
                        .setDatanodeId(r.getDataNodeId())
                        .setDatanodeAddress(r.getDataNodeAddress())
                        .setSize(r.getSize())
                        .setChecksum(r.getChecksum() == null ? "" : r.getChecksum())
                        .setUpdateTime(r.getUpdateTime())
                        .build());
            }

            builder.addBlocks(blockBuilder.build());
        }
        return builder.build();
    }

    public record OpenResult(boolean success, String message, FileMetadata metadata) {
        static OpenResult success(FileMetadata md) {
            return new OpenResult(true, "ok", md);
        }

        static OpenResult failed(String message) {
            return new OpenResult(false, message, FileMetadata.getDefaultInstance());
        }
    }
}
