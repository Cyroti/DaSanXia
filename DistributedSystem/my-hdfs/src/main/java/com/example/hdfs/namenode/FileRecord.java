package com.example.hdfs.namenode;

import java.util.ArrayList;
import java.util.List;

public class FileRecord {
    private String path;
    private long size;
    private long createTime;
    private long modifyTime;
    private long accessTime;
    private List<BlockRecord> blocks = new ArrayList<>();

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(long modifyTime) {
        this.modifyTime = modifyTime;
    }

    public long getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(long accessTime) {
        this.accessTime = accessTime;
    }

    public List<BlockRecord> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockRecord> blocks) {
        this.blocks = blocks;
    }
}
