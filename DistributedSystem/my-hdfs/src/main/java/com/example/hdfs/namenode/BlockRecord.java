package com.example.hdfs.namenode;

import java.util.ArrayList;
import java.util.List;

public class BlockRecord {
    private String blockId;
    private int size;
    private List<ReplicaRecord> replicas = new ArrayList<>();

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public List<ReplicaRecord> getReplicas() {
        return replicas;
    }

    public void setReplicas(List<ReplicaRecord> replicas) {
        this.replicas = replicas;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
