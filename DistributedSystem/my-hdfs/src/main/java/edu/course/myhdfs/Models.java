package edu.course.myhdfs;

import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {
    }

    public enum ConsistencyMode {
        SYNC,
        ASYNC_OBSERVE;

        public static ConsistencyMode from(String raw) {
            if (raw == null) {
                return SYNC;
            }
            String v = raw.trim().toUpperCase();
            if ("ASYNC".equals(v) || "ASYNC_OBSERVE".equals(v) || "OBSERVE".equals(v)) {
                return ASYNC_OBSERVE;
            }
            return SYNC;
        }
    }

    public static class ReplicaInfo {
        public String id;
        public String baseUrl;

        public ReplicaInfo() {
        }

        public ReplicaInfo(String id, String baseUrl) {
            this.id = id;
            this.baseUrl = baseUrl;
        }
    }

    public static class MetadataResponse {
        public String file;
        public String primaryId;
        public String primaryUrl;
        public List<ReplicaInfo> replicas;
    }

    public static class AllocateRequest {
        public String file;
        public int blockSize;
        public int replication;
        public int fileSizeBytes;
    }

    public static class CommitRequest {
        public String file;
        public int fileSizeBytes;
        public int blockSize;
        public int replication;
        public List<BlockMetadata> blocks;
    }

    public static class BlockMetadata {
        public String blockId;
        public int index;
        public int sizeBytes;
        public List<ReplicaInfo> replicas = new ArrayList<>();
    }

    public static class AllocationResponse {
        public String file;
        public int blockSize;
        public int replication;
        public int blockCount;
        public List<BlockMetadata> blocks = new ArrayList<>();
    }

    public static class FileMetadata {
        public String file;
        public int fileSizeBytes;
        public int blockSize;
        public int replication;
        public long createdAt;
        public long updatedAt;
        public List<BlockMetadata> blocks = new ArrayList<>();
    }

    public static class FileListResponse {
        public List<FileMetadata> files = new ArrayList<>();
    }

    public static class BlockWriteRequest {
        public String file;
        public int index;
        public int sizeBytes;
        public String payload;
    }

    public static class BlockWriteAck {
        public boolean ok;
        public String nodeId;
        public String blockId;
        public String file;
        public int index;
        public int sizeBytes;
        public String mode;
        public String message;
    }

    public static class BlockReadResult {
        public String nodeId;
        public String blockId;
        public String file;
        public int index;
        public int sizeBytes;
        public boolean exists;
        public String payload;
    }

    public static class WriteAck {
        public boolean ok;
        public String nodeId;
        public String file;
        public String mode;
        public long seq;
        public String message;
    }

    public static class ReadResult {
        public String nodeId;
        public String file;
        public boolean exists;
        public String value;
        public long lastSeq;
    }

    public static class NodeState {
        public String nodeId;
        public long forwardDelayMs;
        public long throttleBytesPerSec;
    }
}
