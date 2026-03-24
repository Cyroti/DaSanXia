package com.example.hdfs.namenode;

public class BlockRecord {
    private String blockId;
    private String dataNodeId;
    private String dataNodeAddress;
    private int size;

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getDataNodeId() {
        return dataNodeId;
    }

    public void setDataNodeId(String dataNodeId) {
        this.dataNodeId = dataNodeId;
    }

    public String getDataNodeAddress() {
        return dataNodeAddress;
    }

    public void setDataNodeAddress(String dataNodeAddress) {
        this.dataNodeAddress = dataNodeAddress;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
