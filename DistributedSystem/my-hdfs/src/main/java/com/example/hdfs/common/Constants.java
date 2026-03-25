package com.example.hdfs.common;

public final class Constants {
    public static final int BLOCK_SIZE = 4 * 1024;
    public static final int REPLICATION_FACTOR = 3;
    public static final int REQUIRED_REPLICA_ACKS = REPLICATION_FACTOR;
    public static final int RPC_TIMEOUT_MS = 2000;
    public static final int PIPELINE_FORWARD_TIMEOUT_MS = 2000;

    private Constants() {
    }
}
