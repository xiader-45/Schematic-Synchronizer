package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;

import java.util.List;
import java.util.Objects;

public class ServerBrowserEntry {
    public enum EntryType {
        UP,
        DIRECTORY,
        SCHEMATIC
    }

    private final EntryType type;
    private final String name;
    private final String fullPath;
    private final ServerSchematicInfo schematicInfo;
    private final int fileCount;
    private final List<PlayerPlacementInfo> placements;
    private final boolean cached;

    public ServerBrowserEntry(EntryType type, String name, String fullPath, ServerSchematicInfo schematicInfo, int fileCount, List<PlayerPlacementInfo> placements, boolean cached) {
        this.type = type;
        this.name = name;
        this.fullPath = fullPath;
        this.schematicInfo = schematicInfo;
        this.fileCount = fileCount;
        this.placements = placements != null ? placements : List.of();
        this.cached = cached;
    }

    public static ServerBrowserEntry createUp(String targetPath) {
        return new ServerBrowserEntry(EntryType.UP, "..", targetPath, null, 0, null, false);
    }

    public static ServerBrowserEntry createDirectory(String name, String fullPath, int fileCount) {
        return new ServerBrowserEntry(EntryType.DIRECTORY, name, fullPath, null, fileCount, null, false);
    }

    public static ServerBrowserEntry createSchematic(ServerSchematicInfo info, List<PlayerPlacementInfo> placements, boolean cached) {
        return new ServerBrowserEntry(EntryType.SCHEMATIC, info.getName(), info.getId(), info, 0, placements, cached);
    }

    public EntryType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public ServerSchematicInfo getSchematicInfo() {
        return schematicInfo;
    }

    public int getFileCount() {
        return fileCount;
    }

    public List<PlayerPlacementInfo> getPlacements() {
        return placements;
    }

    public boolean isCached() {
        return cached;
    }

    public boolean isDirectory() {
        return type == EntryType.DIRECTORY;
    }

    public boolean isUp() {
        return type == EntryType.UP;
    }

    public boolean isSchematic() {
        return type == EntryType.SCHEMATIC;
    }

    public IGuiIcon getIcon() {
        if (type == EntryType.UP) {
            return Icons.FILE_ICON_DIR_UP;
        }
        if (type == EntryType.DIRECTORY) {
            return Icons.FILE_ICON_DIR;
        }
        if (schematicInfo != null) {
            String lower = schematicInfo.getId().toLowerCase();
            if (lower.endsWith(".schematic") || lower.endsWith(".schem")) {
                return Icons.FILE_ICON_SCHEMATIC;
            }
        }
        return Icons.FILE_ICON_LITEMATIC;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerBrowserEntry that = (ServerBrowserEntry) o;
        return type == that.type && Objects.equals(fullPath, that.fullPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fullPath);
    }
}
