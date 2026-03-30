package edu.course.myhdfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NameNodeMetadataStore {
    private final Path metadataFile;

    public NameNodeMetadataStore(Path metadataFile) {
        this.metadataFile = metadataFile;
    }

    public synchronized Map<String, Models.FileMetadata> load() throws IOException {
        if (!Files.exists(metadataFile)) {
            return new LinkedHashMap<>();
        }
        String content = Files.readString(metadataFile, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return new LinkedHashMap<>();
        }
        FileMap map = Jsons.GSON.fromJson(content, FileMap.class);
        if (map == null || map.files == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(map.files);
    }

    public synchronized void save(Map<String, Models.FileMetadata> files) throws IOException {
        Files.createDirectories(metadataFile.getParent());
        FileMap wrapper = new FileMap();
        wrapper.files = new LinkedHashMap<>(files);
        String content = Jsons.GSON.toJson(wrapper);
        Files.writeString(metadataFile, content, StandardCharsets.UTF_8);
    }

    private static class FileMap {
        Map<String, Models.FileMetadata> files = new LinkedHashMap<>();
    }
}
