package edu.course.myhdfs;

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
        public String nextUrl;
        public long forwardDelayMs;
        public long throttleBytesPerSec;
    }
}
