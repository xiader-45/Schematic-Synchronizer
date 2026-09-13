package com.schematicsynchronizer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.*;

public class HologramGroupData {
    public static final String PERM_ALL = "all";
    public static final String PERM_EDIT = "edit";
    public static final String PERM_ADD = "add";
    public static final String PERM_READ = "read";

    private final String id;
    private String name;
    private final String dimension;
    private UUID ownerUuid;
    private String ownerName;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Map<UUID, String> memberPermissions = new LinkedHashMap<>();
    private final List<GroupPlacementData> placements = new ArrayList<>();
    private long lastModified;

    public HologramGroupData(String id, String name, String dimension,
                             UUID ownerUuid, String ownerName, Map<UUID, String> members,
                             List<GroupPlacementData> placements, long lastModified) {
        this.id = id;
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Group " + id;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName != null ? ownerName : "Player";
        if (members != null) {
            this.members.putAll(members);
        }
        if (ownerUuid != null && ownerName != null) {
            this.members.put(ownerUuid, ownerName);
        }
        if (placements != null) {
            this.placements.addAll(placements);
        }
        this.lastModified = lastModified > 0 ? lastModified : System.currentTimeMillis();
    }

    // Legacy compatibility constructor
    public HologramGroupData(String id, String name, String schematicId, String dimension,
                             UUID ownerUuid, String ownerName, Map<UUID, String> members,
                             BlockPos origin, String rotation, String mirror, boolean locked, long lastModified) {
        this(id, name, dimension, ownerUuid, ownerName, members, null, lastModified);
        if (schematicId != null && !schematicId.isEmpty()) {
            this.placements.add(new GroupPlacementData(id + "_p0", name, schematicId, origin, rotation, mirror, locked));
        }
    }

    public static HologramGroupData read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
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

        int permCount = buf.readVarInt();
        Map<UUID, String> perms = new LinkedHashMap<>();
        for (int i = 0; i < permCount; i++) {
            UUID u = buf.readUUID();
            String p = buf.readUtf();
            perms.put(u, p);
        }

        int placementCount = buf.readVarInt();
        List<GroupPlacementData> placements = new ArrayList<>(placementCount);
        for (int i = 0; i < placementCount; i++) {
            placements.add(GroupPlacementData.read(buf));
        }

        long lastModified = buf.readLong();

        HologramGroupData group = new HologramGroupData(id, name, dimension, ownerUuid, ownerName, members, placements, lastModified);
        group.memberPermissions.putAll(perms);
        return group;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);
        buf.writeUtf(dimension);
        buf.writeUUID(ownerUuid);
        buf.writeUtf(ownerName);

        buf.writeVarInt(members.size());
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }

        buf.writeVarInt(memberPermissions.size());
        for (Map.Entry<UUID, String> entry : memberPermissions.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }

        buf.writeVarInt(placements.size());
        for (GroupPlacementData p : placements) {
            p.write(buf);
        }

        buf.writeLong(lastModified);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
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

        JsonArray permsArr = new JsonArray();
        for (Map.Entry<UUID, String> entry : memberPermissions.entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("uuid", entry.getKey().toString());
            p.addProperty("permission", entry.getValue());
            permsArr.add(p);
        }
        obj.add("permissions", permsArr);

        JsonArray placementsArr = new JsonArray();
        for (GroupPlacementData p : placements) {
            placementsArr.add(p.toJson());
        }
        obj.add("placements", placementsArr);

        obj.addProperty("lastModified", lastModified);
        return obj;
    }

    public static HologramGroupData fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String name = obj.has("name") ? obj.get("name").getAsString() : id;
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

        Map<UUID, String> perms = new LinkedHashMap<>();
        if (obj.has("permissions") && obj.get("permissions").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("permissions")) {
                if (el.isJsonObject()) {
                    JsonObject p = el.getAsJsonObject();
                    try {
                        UUID u = UUID.fromString(p.get("uuid").getAsString());
                        String perm = p.has("permission") ? p.get("permission").getAsString() : PERM_READ;
                        perms.put(u, perm);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        List<GroupPlacementData> placements = new ArrayList<>();
        if (obj.has("placements") && obj.get("placements").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("placements")) {
                if (el.isJsonObject()) {
                    try {
                        placements.add(GroupPlacementData.fromJson(el.getAsJsonObject()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } else if (obj.has("schematicId")) {
            // Legacy format fallback
            String schematicId = obj.get("schematicId").getAsString();
            BlockPos origin = BlockPos.ZERO;
            if (obj.has("origin") && obj.get("origin").isJsonObject()) {
                JsonObject p = obj.getAsJsonObject("origin");
                origin = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
            }
            String rotation = obj.has("rotation") ? obj.get("rotation").getAsString() : "NONE";
            String mirror = obj.has("mirror") ? obj.get("mirror").getAsString() : "NONE";
            boolean locked = obj.has("locked") && obj.get("locked").getAsBoolean();
            placements.add(new GroupPlacementData(id + "_p0", name, schematicId, origin, rotation, mirror, locked));
        }

        long lastModified = obj.has("lastModified") ? obj.get("lastModified").getAsLong() : System.currentTimeMillis();

        HologramGroupData group = new HologramGroupData(id, name, dimension, ownerUuid, ownerName, members, placements, lastModified);
        group.memberPermissions.putAll(perms);
        return group;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.lastModified = System.currentTimeMillis();
    }

    public String getDimension() {
        return dimension;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.members.put(ownerUuid, ownerName);
        this.lastModified = System.currentTimeMillis();
    }

    public Map<UUID, String> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public void addMember(UUID uuid, String name) {
        this.members.put(uuid, name);
        this.lastModified = System.currentTimeMillis();
    }

    public void removeMember(UUID uuid) {
        this.members.remove(uuid);
        this.memberPermissions.remove(uuid);
        this.lastModified = System.currentTimeMillis();
    }

    public boolean isOwner(UUID uuid) {
        return this.ownerUuid != null && this.ownerUuid.equals(uuid);
    }

    public boolean isOwner(UUID uuid, String playerName) {
        if (isOwner(uuid)) return true;
        return playerName != null && this.ownerName != null && playerName.equalsIgnoreCase(this.ownerName);
    }

    public boolean isMember(UUID uuid) {
        return this.members.containsKey(uuid);
    }

    public boolean isMember(UUID uuid, String playerName) {
        if (isMember(uuid)) return true;
        if (playerName != null) {
            for (String n : this.members.values()) {
                if (n.equalsIgnoreCase(playerName)) return true;
            }
        }
        return false;
    }

    public String getMemberPermission(UUID uuid) {
        return getMemberPermission(uuid, null);
    }

    public String getMemberPermission(UUID uuid, String playerName) {
        if (isOwner(uuid, playerName)) {
            return PERM_ALL;
        }
        if (uuid != null && this.memberPermissions.containsKey(uuid)) {
            return this.memberPermissions.get(uuid);
        }
        if (playerName != null) {
            for (Map.Entry<UUID, String> entry : this.members.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(playerName)) {
                    return this.memberPermissions.getOrDefault(entry.getKey(), PERM_READ);
                }
            }
        }
        return PERM_READ;
    }

    public void setMemberPermission(UUID uuid, String permission) {
        if (uuid == null) return;
        if (permission == null || (!permission.equals(PERM_ALL) && !permission.equals(PERM_EDIT) && !permission.equals(PERM_ADD))) {
            permission = PERM_READ;
        }
        this.memberPermissions.put(uuid, permission);
        this.lastModified = System.currentTimeMillis();
    }

    public int getPlacementCountForMember(UUID memberUuid) {
        if (memberUuid == null) return 0;
        int count = 0;
        for (GroupPlacementData p : this.placements) {
            if (p.getCreatorUuid() != null) {
                if (memberUuid.equals(p.getCreatorUuid())) {
                    count++;
                }
            } else if (this.ownerUuid != null && this.ownerUuid.equals(memberUuid)) {
                count++;
            }
        }
        return count;
    }

    public List<GroupPlacementData> getPlacements() {
        return Collections.unmodifiableList(placements);
    }

    public void addPlacement(GroupPlacementData placement) {
        if (placement == null) return;
        this.placements.removeIf(p -> p.getId().equals(placement.getId()));
        this.placements.add(placement);
        this.lastModified = System.currentTimeMillis();
    }

    public void removePlacement(String placementId) {
        if (placementId == null) return;
        this.placements.removeIf(p -> p.getId().equals(placementId));
        this.lastModified = System.currentTimeMillis();
    }

    public GroupPlacementData getPlacement(String placementId) {
        if (placementId == null) return null;
        for (GroupPlacementData p : placements) {
            if (p.getId().equals(placementId)) return p;
        }
        return null;
    }

    // Compatibility getters that return the first placement's values (or defaults)
    public String getSchematicId() {
        return !placements.isEmpty() ? placements.get(0).getSchematicId() : "";
    }

    public BlockPos getOrigin() {
        return !placements.isEmpty() ? placements.get(0).getOrigin() : BlockPos.ZERO;
    }

    public String getRotation() {
        return !placements.isEmpty() ? placements.get(0).getRotation() : "NONE";
    }

    public String getMirror() {
        return !placements.isEmpty() ? placements.get(0).getMirror() : "NONE";
    }

    public boolean isLocked() {
        return !placements.isEmpty() && placements.get(0).isLocked();
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HologramGroupData that = (HologramGroupData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
