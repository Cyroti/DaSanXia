package com.example.hdfs.namenode;

public class ReplicaRecord {
    private String dataNodeId;
    private String dataNodeAddress;
    private int size;
    private String checksum;
    private long updateTime;

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

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}
