package com.example.hdfs.namenode;

import com.example.hdfs.common.Constants;
import com.example.hdfs.common.DataNodeEndpoint;
import com.example.hdfs.rpc.BlockInfo;
import com.example.hdfs.rpc.FileMetadata;
import com.example.hdfs.rpc.OpenMode;

import java.util.ArrayList;
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
        } else if (mode == OpenMode.READ) {
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
        DataNodeEndpoint ep = pickDataNode();
        BlockRecord block = new BlockRecord();
        block.setBlockId(UUID.randomUUID().toString());
        block.setDataNodeId(ep.id());
        block.setDataNodeAddress(ep.address());
        block.setSize(0);

        file.getBlocks().add(block);
        file.setModifyTime(System.currentTimeMillis());
        store.save(fsImage);
        return block;
    }

    public synchronized boolean updateBlockSize(String path, String blockId, String dataNodeId, String dataNodeAddress, int blockSize) {
        FileRecord file = fsImage.getFiles().get(path);
        if (file == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(writeOpen.get(path))) {
            return false;
        }
        if (blockSize < 0 || blockSize > Constants.BLOCK_SIZE) {
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
            target = new BlockRecord();
            target.setBlockId(blockId);
            target.setDataNodeId(dataNodeId);
            target.setDataNodeAddress(dataNodeAddress);
            target.setSize(blockSize);
            file.getBlocks().add(target);
        } else {
            target.setDataNodeId(dataNodeId);
            target.setDataNodeAddress(dataNodeAddress);
            target.setSize(blockSize);
        }

        long total = file.getBlocks().stream().map(BlockRecord::getSize).reduce(0, Integer::sum);
        file.setSize(total);
        long now = System.currentTimeMillis();
        file.setModifyTime(now);
        file.setAccessTime(now);
        store.save(fsImage);
        return true;
    }

    private DataNodeEndpoint pickDataNode() {
        int idx = Math.floorMod(dataNodeCursor.getAndIncrement(), dataNodes.size());
        return dataNodes.get(idx);
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
            builder.addBlocks(BlockInfo.newBuilder()
                    .setBlockId(b.getBlockId())
                    .setDatanodeId(b.getDataNodeId())
                    .setDatanodeAddress(b.getDataNodeAddress())
                    .setSize(b.getSize())
                    .build());
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
