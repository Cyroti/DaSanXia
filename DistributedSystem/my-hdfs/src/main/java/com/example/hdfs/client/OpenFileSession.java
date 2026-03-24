package com.example.hdfs.client;

import com.example.hdfs.rpc.FileMetadata;
import com.example.hdfs.rpc.OpenMode;

public class OpenFileSession {
    private final int fd;
    private final String path;
    private final OpenMode mode;
    private FileMetadata metadata;

    public OpenFileSession(int fd, String path, OpenMode mode, FileMetadata metadata) {
        this.fd = fd;
        this.path = path;
        this.mode = mode;
        this.metadata = metadata;
    }

    public int getFd() {
        return fd;
    }

    public String getPath() {
        return path;
    }

    public OpenMode getMode() {
        return mode;
    }

    public FileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(FileMetadata metadata) {
        this.metadata = metadata;
    }
}
