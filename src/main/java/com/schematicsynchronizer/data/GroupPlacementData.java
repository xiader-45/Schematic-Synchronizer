package com.schematicsynchronizer.data;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public class GroupPlacementData {
    private final String id;
    private String name;
    private final String schematicId;
    private BlockPos origin;
    private String rotation;
    private String mirror;
    private boolean locked;

    public GroupPlacementData(String id, String name, String schematicId, BlockPos origin, String rotation, String mirror, boolean locked) {
        this.id = (id != null && !id.isEmpty()) ? id : UUID.randomUUID().toString().substring(0, 8);
        this.name = (name != null && !name.isEmpty()) ? name : "Placement " + this.id;
        this.schematicId = (schematicId != null) ? schematicId : "";
        this.origin = (origin != null) ? origin : BlockPos.ZERO;
        this.rotation = (rotation != null) ? rotation : "NONE";
        this.mirror = (mirror != null) ? mirror : "NONE";
        this.locked = locked;
    }

    public static GroupPlacementData read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        String schematicId = buf.readUtf();
        BlockPos origin = buf.readBlockPos();
        String rotation = buf.readUtf();
        String mirror = buf.readUtf();
        boolean locked = buf.readBoolean();
        return new GroupPlacementData(id, name, schematicId, origin, rotation, mirror, locked);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id != null ? id : "");
        buf.writeUtf(name != null ? name : "");
        buf.writeUtf(schematicId != null ? schematicId : "");
        buf.writeBlockPos(origin != null ? origin : BlockPos.ZERO);
        buf.writeUtf(rotation != null ? rotation : "NONE");
        buf.writeUtf(mirror != null ? mirror : "NONE");
        buf.writeBoolean(locked);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("schematicId", schematicId);

        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", origin.getX());
        posObj.addProperty("y", origin.getY());
        posObj.addProperty("z", origin.getZ());
        obj.add("origin", posObj);

        obj.addProperty("rotation", rotation);
        obj.addProperty("mirror", mirror);
        obj.addProperty("locked", locked);
        return obj;
    }

    public static GroupPlacementData fromJson(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : UUID.randomUUID().toString().substring(0, 8);
        String name = obj.has("name") ? obj.get("name").getAsString() : id;
        String schematicId = obj.has("schematicId") ? obj.get("schematicId").getAsString() : "";

        BlockPos origin = BlockPos.ZERO;
        if (obj.has("origin") && obj.get("origin").isJsonObject()) {
            JsonObject p = obj.getAsJsonObject("origin");
            origin = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
        }

        String rotation = obj.has("rotation") ? obj.get("rotation").getAsString() : "NONE";
        String mirror = obj.has("mirror") ? obj.get("mirror").getAsString() : "NONE";
        boolean locked = obj.has("locked") && obj.get("locked").getAsBoolean();

        return new GroupPlacementData(id, name, schematicId, origin, rotation, mirror, locked);
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

    public BlockPos getOrigin() {
        return origin;
    }

    public void setOrigin(BlockPos origin) {
        this.origin = origin != null ? origin : BlockPos.ZERO;
    }

    public String getRotation() {
        return rotation;
    }

    public void setRotation(String rotation) {
        this.rotation = rotation != null ? rotation : "NONE";
    }

    public String getMirror() {
        return mirror;
    }

    public void setMirror(String mirror) {
        this.mirror = mirror != null ? mirror : "NONE";
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
