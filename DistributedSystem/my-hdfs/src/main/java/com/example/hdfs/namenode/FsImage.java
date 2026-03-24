package com.example.hdfs.namenode;

import java.util.HashMap;
import java.util.Map;

public class FsImage {
    private Map<String, FileRecord> files = new HashMap<>();

    public Map<String, FileRecord> getFiles() {
        return files;
    }

    public void setFiles(Map<String, FileRecord> files) {
        this.files = files;
    }
}
