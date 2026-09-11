package com.schematicsynchronizer.data;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.Objects;

public class ServerSchematicInfo {
    private final String id;
    private final String name;
    private final long size;
    private final String hash;
    private final long modifiedTime;

    // Metadata
    private final String author;
    private final long timeCreated;
    private final int regionCount;
    private final int totalVolume;
    private final int totalBlocks;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int minecraftDataVersion;

    public ServerSchematicInfo(String id, String name, long size, String hash, long modifiedTime) {
        this(id, name, size, hash, modifiedTime, "", modifiedTime, 1, 0, 0, 0, 0, 0, 0);
    }

    public ServerSchematicInfo(String id, String name, long size, String hash, long modifiedTime,
                               String author, long timeCreated, int regionCount,
                               int totalVolume, int totalBlocks,
                               int sizeX, int sizeY, int sizeZ, int minecraftDataVersion) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.hash = hash;
        this.modifiedTime = modifiedTime;
        this.author = author != null ? author : "";
        this.timeCreated = timeCreated > 0 ? timeCreated : modifiedTime;
        this.regionCount = regionCount > 0 ? regionCount : 1;
        this.totalVolume = totalVolume;
        this.totalBlocks = totalBlocks;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.minecraftDataVersion = minecraftDataVersion;
    }

    public static ServerSchematicInfo read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        long size = buf.readLong();
        String hash = buf.readUtf();
        long modifiedTime = buf.readLong();

        String author = buf.readUtf();
        long timeCreated = buf.readLong();
        int regionCount = buf.readInt();
        int totalVolume = buf.readInt();
        int totalBlocks = buf.readInt();
        int sizeX = buf.readInt();
        int sizeY = buf.readInt();
        int sizeZ = buf.readInt();
        int minecraftDataVersion = buf.readInt();

        return new ServerSchematicInfo(id, name, size, hash, modifiedTime,
                author, timeCreated, regionCount, totalVolume, totalBlocks,
                sizeX, sizeY, sizeZ, minecraftDataVersion);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);
        buf.writeLong(size);
        buf.writeUtf(hash);
        buf.writeLong(modifiedTime);

        buf.writeUtf(author);
        buf.writeLong(timeCreated);
        buf.writeInt(regionCount);
        buf.writeInt(totalVolume);
        buf.writeInt(totalBlocks);
        buf.writeInt(sizeX);
        buf.writeInt(sizeY);
        buf.writeInt(sizeZ);
        buf.writeInt(minecraftDataVersion);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public String getAuthor() {
        return author;
    }

    public long getTimeCreated() {
        return timeCreated;
    }

    public int getRegionCount() {
        return regionCount;
    }

    public int getTotalVolume() {
        return totalVolume;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getMinecraftDataVersion() {
        return minecraftDataVersion;
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerSchematicInfo that = (ServerSchematicInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
