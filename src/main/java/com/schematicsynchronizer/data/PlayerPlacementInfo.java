package com.schematicsynchronizer.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

public class PlayerPlacementInfo {
    private final UUID placementId;
    private final String schematicId;
    private final String ownerName;
    private final UUID ownerUuid;
    private final BlockPos pos;
    private final String dimension;
    private final String rotation;
    private final String mirror;
    private final long timestamp;

    public PlayerPlacementInfo(UUID placementId, String schematicId, String ownerName, UUID ownerUuid,
                               BlockPos pos, String dimension, String rotation, String mirror, long timestamp) {
        this.placementId = placementId;
        this.schematicId = schematicId;
        this.ownerName = ownerName;
        this.ownerUuid = ownerUuid;
        this.pos = pos;
        this.dimension = dimension;
        this.rotation = rotation;
        this.mirror = mirror;
        this.timestamp = timestamp;
    }

    public static PlayerPlacementInfo read(RegistryFriendlyByteBuf buf) {
        UUID placementId = buf.readUUID();
        String schematicId = buf.readUtf();
        String ownerName = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        String dimension = buf.readUtf();
        String rotation = buf.readUtf();
        String mirror = buf.readUtf();
        long timestamp = buf.readLong();
        return new PlayerPlacementInfo(placementId, schematicId, ownerName, ownerUuid, pos, dimension, rotation, mirror, timestamp);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(placementId);
        buf.writeUtf(schematicId);
        buf.writeUtf(ownerName);
        buf.writeUUID(ownerUuid);
        buf.writeBlockPos(pos);
        buf.writeUtf(dimension);
        buf.writeUtf(rotation);
        buf.writeUtf(mirror);
        buf.writeLong(timestamp);
    }

    public UUID getPlacementId() {
        return placementId;
    }

    public String getSchematicId() {
        return schematicId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getDimension() {
        return dimension;
    }

    public String getRotation() {
        return rotation;
    }

    public String getMirror() {
        return mirror;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerPlacementInfo that = (PlayerPlacementInfo) o;
        return Objects.equals(placementId, that.placementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placementId);
    }
}
