package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.server.ServerSchematicManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public class LocalFileEntry {
    public enum EntryType {
        UP,
        DIRECTORY,
        SCHEMATIC
    }

    private final EntryType type;
    private final Path path;
    private final String name;
    private final long size;
    private final long modified;
    private final int fileCount;
    private ServerSchematicManager.SchematicMetadataDetails metadata;

    public LocalFileEntry(EntryType type, Path path, String name, long size, long modified, int fileCount) {
        this.type = type;
        this.path = path;
        this.name = name;
        this.size = size;
        this.modified = modified;
        this.fileCount = fileCount;
    }

    public static LocalFileEntry up(Path parentPath) {
        return new LocalFileEntry(EntryType.UP, parentPath, "..", 0, 0, 0);
    }

    public static LocalFileEntry directory(Path dirPath, int fileCount) {
        return new LocalFileEntry(EntryType.DIRECTORY, dirPath, dirPath.getFileName().toString(), 0, 0, fileCount);
    }

    public static LocalFileEntry schematic(Path filePath) {
        String fileName = filePath.getFileName().toString();
        long size = 0;
        long modified = 0;
        try {
            size = Files.size(filePath);
            modified = Files.getLastModifiedTime(filePath).toMillis();
        } catch (Exception ignored) {
        }
        return new LocalFileEntry(EntryType.SCHEMATIC, filePath, fileName, size, modified, 0);
    }

    public EntryType getType() {
        return type;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getModified() {
        return modified;
    }

    public int getFileCount() {
        return fileCount;
    }

    public boolean isUp() {
        return type == EntryType.UP;
    }

    public boolean isDirectory() {
        return type == EntryType.DIRECTORY;
    }

    public boolean isSchematic() {
        return type == EntryType.SCHEMATIC;
    }

    public ServerSchematicManager.SchematicMetadataDetails getMetadata() {
        if (this.metadata == null && isSchematic() && path != null && Files.exists(path)) {
            this.metadata = ServerSchematicManager.extractMetadata(path, modified);
        }
        return this.metadata;
    }

    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", size / (1024.0 * 1024.0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalFileEntry that = (LocalFileEntry) o;
        return type == that.type && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, path);
    }
}
