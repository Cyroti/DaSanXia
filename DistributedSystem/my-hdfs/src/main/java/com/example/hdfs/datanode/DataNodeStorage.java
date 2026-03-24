package com.example.hdfs.datanode;

import com.example.hdfs.common.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DataNodeStorage {
    private final String dataNodeId;
    private final Path storageDir;

    public DataNodeStorage(String dataNodeId, Path storageDir) {
        this.dataNodeId = dataNodeId;
        this.storageDir = storageDir;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + storageDir, e);
        }
    }

    public synchronized byte[] read(String blockId) {
        Path p = blockPath(blockId);
        try {
            if (!Files.exists(p)) {
                return null;
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read block " + blockId, e);
        }
    }

    public synchronized int append(String blockId, byte[] bytes) {
        Path p = blockPath(blockId);
        byte[] existing = read(blockId);
        int existingSize = existing == null ? 0 : existing.length;
        if (existingSize + bytes.length > Constants.BLOCK_SIZE) {
            throw new IllegalArgumentException("Block overflow, max size is " + Constants.BLOCK_SIZE);
        }

        try (OutputStream os = Files.newOutputStream(
                p,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        )) {
            os.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append block " + blockId, e);
        }

        return existingSize + bytes.length;
    }

    public String getDataNodeId() {
        return dataNodeId;
    }

    private Path blockPath(String blockId) {
        return storageDir.resolve(blockId + ".blk");
    }
}
