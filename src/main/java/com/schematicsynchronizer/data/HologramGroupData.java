package com.schematicsynchronizer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.*;

public class HologramGroupData {
    private final String id;
    private String name;
    private final String schematicId;
    private final String dimension;
    private UUID ownerUuid;
    private String ownerName;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private BlockPos origin;
    private String rotation;
    private String mirror;
    private boolean locked;
    private long lastModified;

    public HologramGroupData(String id, String name, String schematicId, String dimension,
                             UUID ownerUuid, String ownerName, Map<UUID, String> members,
                             BlockPos origin, String rotation, String mirror, boolean locked, long lastModified) {
        this.id = id;
        this.name = name;
        this.schematicId = schematicId;
        this.dimension = dimension;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        if (members != null) {
            this.members.putAll(members);
        }
        if (ownerUuid != null && ownerName != null) {
            this.members.put(ownerUuid, ownerName);
        }
        this.origin = origin != null ? origin : BlockPos.ZERO;
        this.rotation = rotation != null ? rotation : "NONE";
        this.mirror = mirror != null ? mirror : "NONE";
        this.locked = locked;
        this.lastModified = lastModified > 0 ? lastModified : System.currentTimeMillis();
    }

    public static HologramGroupData read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        String schematicId = buf.readUtf();
        String dimension = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readUtf();

        int memberCount = buf.readVarInt();
        Map<UUID, String> members = new LinkedHashMap<>();
        for (int i = 0; i < memberCount; i++) {
            UUID u = buf.readUUID();
            String n = buf.readUtf();
            members.put(u, n);
        }

        BlockPos origin = buf.readBlockPos();
        String rotation = buf.readUtf();
        String mirror = buf.readUtf();
        boolean locked = buf.readBoolean();
        long lastModified = buf.readLong();

        return new HologramGroupData(id, name, schematicId, dimension, ownerUuid, ownerName, members,
                origin, rotation, mirror, locked, lastModified);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);
        buf.writeUtf(schematicId);
        buf.writeUtf(dimension);
        buf.writeUUID(ownerUuid);
        buf.writeUtf(ownerName);

        buf.writeVarInt(members.size());
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }

        buf.writeBlockPos(origin);
        buf.writeUtf(rotation);
        buf.writeUtf(mirror);
        buf.writeBoolean(locked);
        buf.writeLong(lastModified);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("schematicId", schematicId);
        obj.addProperty("dimension", dimension);
        obj.addProperty("ownerUuid", ownerUuid.toString());
        obj.addProperty("ownerName", ownerName);

        JsonArray membersArr = new JsonArray();
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            JsonObject m = new JsonObject();
            m.addProperty("uuid", entry.getKey().toString());
            m.addProperty("name", entry.getValue());
            membersArr.add(m);
        }
        obj.add("members", membersArr);

        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", origin.getX());
        posObj.addProperty("y", origin.getY());
        posObj.addProperty("z", origin.getZ());
        obj.add("origin", posObj);

        obj.addProperty("rotation", rotation);
        obj.addProperty("mirror", mirror);
        obj.addProperty("locked", locked);
        obj.addProperty("lastModified", lastModified);
        return obj;
    }

    public static HologramGroupData fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String name = obj.has("name") ? obj.get("name").getAsString() : id;
        String schematicId = obj.get("schematicId").getAsString();
        String dimension = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
        UUID ownerUuid = UUID.fromString(obj.get("ownerUuid").getAsString());
        String ownerName = obj.has("ownerName") ? obj.get("ownerName").getAsString() : "Unknown";

        Map<UUID, String> members = new LinkedHashMap<>();
        if (obj.has("members") && obj.get("members").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("members")) {
                if (el.isJsonObject()) {
                    JsonObject m = el.getAsJsonObject();
                    try {
                        UUID u = UUID.fromString(m.get("uuid").getAsString());
                        String n = m.has("name") ? m.get("name").getAsString() : "Player";
                        members.put(u, n);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        BlockPos origin = BlockPos.ZERO;
        if (obj.has("origin") && obj.get("origin").isJsonObject()) {
            JsonObject p = obj.getAsJsonObject("origin");
            origin = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
        }

        String rotation = obj.has("rotation") ? obj.get("rotation").getAsString() : "NONE";
        String mirror = obj.has("mirror") ? obj.get("mirror").getAsString() : "NONE";
        boolean locked = obj.has("locked") && obj.get("locked").getAsBoolean();
        long lastModified = obj.has("lastModified") ? obj.get("lastModified").getAsLong() : System.currentTimeMillis();

        return new HologramGroupData(id, name, schematicId, dimension, ownerUuid, ownerName, members,
                origin, rotation, mirror, locked, lastModified);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchematicId() {
        return schematicId;
    }

    public String getDimension() {
        return dimension;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.members.put(ownerUuid, ownerName);
        this.lastModified = System.currentTimeMillis();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid != null && ownerUuid.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public void addMember(UUID uuid, String name) {
        this.members.put(uuid, name);
        this.lastModified = System.currentTimeMillis();
    }

    public void removeMember(UUID uuid) {
        this.members.remove(uuid);
        this.lastModified = System.currentTimeMillis();
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public void setOrigin(BlockPos origin) {
        this.origin = origin;
        this.lastModified = System.currentTimeMillis();
    }

    public String getRotation() {
        return rotation;
    }

    public void setRotation(String rotation) {
        this.rotation = rotation;
        this.lastModified = System.currentTimeMillis();
    }

    public String getMirror() {
        return mirror;
    }

    public void setMirror(String mirror) {
        this.mirror = mirror;
        this.lastModified = System.currentTimeMillis();
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        this.lastModified = System.currentTimeMillis();
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}
