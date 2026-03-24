package com.example.hdfs.namenode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FsImageStore {
    private final Path fsImagePath;
    private final ObjectMapper objectMapper;

    public FsImageStore(Path fsImagePath) {
        this.fsImagePath = fsImagePath;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public synchronized FsImage load() {
        try {
            if (!Files.exists(fsImagePath)) {
                return new FsImage();
            }
            return objectMapper.readValue(Files.newInputStream(fsImagePath), FsImage.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FsImage from " + fsImagePath, e);
        }
    }

    public synchronized void save(FsImage fsImage) {
        try {
            Path parent = fsImagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(Files.newOutputStream(fsImagePath), fsImage);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save FsImage to " + fsImagePath, e);
        }
    }
}
