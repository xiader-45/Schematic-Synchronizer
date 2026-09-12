package com.schematicsynchronizer.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.CreateHologramGroupPayload;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import com.schematicsynchronizer.network.SyncHologramGroupsPayload;
import com.schematicsynchronizer.network.UpdateHologramPlacementPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ServerHologramGroupManager {
    private static final ServerHologramGroupManager INSTANCE = new ServerHologramGroupManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // dimension -> Map<groupId, HologramGroupData>
    private final Map<String, Map<String, HologramGroupData>> groupsByDimension = new ConcurrentHashMap<>();

    public static ServerHologramGroupManager getInstance() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        loadAllGroups(server);
    }

    public static Path getGroupFilePath(MinecraftServer server, String dimension, String groupId) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        String dimPath = dimension != null ? dimension.trim() : "minecraft:overworld";
        if (dimPath.contains(":")) {
            String[] parts = dimPath.split(":", 2);
            dimPath = parts[0] + "/" + parts[1];
        }
        String safeName = sanitizeFilename(groupId);
        return worldRoot.resolve("dimensions")
                .resolve(dimPath)
                .resolve("data")
                .resolve("schematics")
                .resolve(safeName + ".json");
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) return "group";
        String clean = name.trim().replaceAll("[:\\\\/*?\"<>|]", "_");
        while (clean.endsWith(".") || clean.endsWith(" ")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.isEmpty() ? "group" : clean;
    }

    public synchronized void loadAllGroups(MinecraftServer server) {
        groupsByDimension.clear();
        if (server == null) return;

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path dimensionsBase = worldRoot.resolve("dimensions");
        if (!Files.exists(dimensionsBase)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(dimensionsBase, 6)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(filePath -> {
                        // Check if file is inside data/schematics/
                        Path parent = filePath.getParent();
                        if (parent != null && parent.getFileName().toString().equalsIgnoreCase("schematics")) {
                            Path dataDir = parent.getParent();
                            if (dataDir != null && dataDir.getFileName().toString().equalsIgnoreCase("data")) {
                                try (Reader reader = Files.newBufferedReader(filePath)) {
                                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                    HologramGroupData data = HologramGroupData.fromJson(obj);
                                    if (data != null && data.getId() != null && data.getDimension() != null) {
                                        groupsByDimension.computeIfAbsent(data.getDimension(), k -> new ConcurrentHashMap<>())
                                                .put(data.getId(), data);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    public synchronized void saveGroup(MinecraftServer server, HologramGroupData group) {
        if (server == null || group == null) return;
        Path path = getGroupFilePath(server, group.getDimension(), group.getId());
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(group.toJson(), writer);
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void deleteGroup(MinecraftServer server, HologramGroupData group) {
        if (server == null || group == null) return;
        Map<String, HologramGroupData> map = groupsByDimension.get(group.getDimension());
        if (map != null) {
            map.remove(group.getId());
        }
        Path path = getGroupFilePath(server, group.getDimension(), group.getId());
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    public List<HologramGroupData> getGroupsForDimension(String dimension) {
        if (dimension == null) return Collections.emptyList();
        Map<String, HologramGroupData> map = groupsByDimension.get(dimension);
        if (map == null) return Collections.emptyList();
        return new ArrayList<>(map.values());
    }

    public HologramGroupData getGroup(String dimension, String groupId) {
        if (dimension == null || groupId == null) return null;
        Map<String, HologramGroupData> map = groupsByDimension.get(dimension);
        return map != null ? map.get(groupId) : null;
    }

    public HologramGroupData findGroupById(String groupId) {
        if (groupId == null) return null;
        for (Map<String, HologramGroupData> map : groupsByDimension.values()) {
            HologramGroupData g = map.get(groupId);
            if (g != null) return g;
        }
        return null;
    }

    public void createGroup(MinecraftServer server, ServerPlayer player, CreateHologramGroupPayload payload) {
        String dimension = payload.dimension();
        if (dimension == null || dimension.isEmpty()) {
            dimension = player.level().dimension().identifier().toString();
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        String name = payload.name() != null && !payload.name().trim().isEmpty() ? payload.name().trim() : "Group " + id;

        HologramGroupData group = new HologramGroupData(
                id,
                name,
                payload.schematicId(),
                dimension,
                player.getUUID(),
                player.getName().getString(),
                Collections.singletonMap(player.getUUID(), player.getName().getString()),
                payload.origin(),
                payload.rotation(),
                payload.mirror(),
                false,
                System.currentTimeMillis()
        );

        groupsByDimension.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>()).put(group.getId(), group);
        saveGroup(server, group);
        broadcastGroups(server, dimension);
    }

    public void joinGroup(MinecraftServer server, ServerPlayer player, String groupId) {
        HologramGroupData group = findGroupById(groupId);
        if (group == null) return;

        group.addMember(player.getUUID(), player.getName().getString());
        saveGroup(server, group);
        broadcastGroups(server, group.getDimension());
    }

    public void leaveGroup(MinecraftServer server, ServerPlayer player, String groupId, int action) {
        HologramGroupData group = findGroupById(groupId);
        if (group == null) return;

        UUID playerUuid = player.getUUID();
        if (group.isOwner(playerUuid)) {
            // Owner is leaving
            if (action == LeaveHologramGroupPayload.ACTION_DELETE || group.getMembers().size() <= 1) {
                // Delete group
                deleteGroup(server, group);
                broadcastGroups(server, group.getDimension());
            } else if (action == LeaveHologramGroupPayload.ACTION_TRANSFER_RANDOM) {
                // Transfer ownership to random member
                List<Map.Entry<UUID, String>> otherMembers = new ArrayList<>();
                for (Map.Entry<UUID, String> entry : group.getMembers().entrySet()) {
                    if (!entry.getKey().equals(playerUuid)) {
                        otherMembers.add(entry);
                    }
                }
                if (!otherMembers.isEmpty()) {
                    Map.Entry<UUID, String> newOwner = otherMembers.get(new Random().nextInt(otherMembers.size()));
                    group.removeMember(playerUuid);
                    group.setOwner(newOwner.getKey(), newOwner.getValue());
                    saveGroup(server, group);
                    broadcastGroups(server, group.getDimension());
                } else {
                    deleteGroup(server, group);
                    broadcastGroups(server, group.getDimension());
                }
            }
        } else {
            // Regular member is leaving
            group.removeMember(playerUuid);
            saveGroup(server, group);
            broadcastGroups(server, group.getDimension());
        }
    }

    public void kickMember(MinecraftServer server, ServerPlayer player, String groupId, UUID targetUuid) {
        HologramGroupData group = findGroupById(groupId);
        if (group == null || targetUuid == null) return;

        if (!group.isOwner(player.getUUID())) {
            return; // Only owner can kick
        }
        if (group.isOwner(targetUuid)) {
            return; // Cannot kick self
        }

        group.removeMember(targetUuid);
        saveGroup(server, group);
        broadcastGroups(server, group.getDimension());
    }

    public void transferOwnership(MinecraftServer server, ServerPlayer player, String groupId, UUID newOwnerUuid) {
        HologramGroupData group = findGroupById(groupId);
        if (group == null || newOwnerUuid == null) return;

        if (!group.isOwner(player.getUUID())) {
            return; // Only owner can transfer
        }
        String newOwnerName = group.getMembers().get(newOwnerUuid);
        if (newOwnerName == null) {
            return; // Must be an existing member
        }

        group.setOwner(newOwnerUuid, newOwnerName);
        saveGroup(server, group);
        broadcastGroups(server, group.getDimension());
    }

    public void updatePlacement(MinecraftServer server, ServerPlayer player, UpdateHologramPlacementPayload payload) {
        HologramGroupData group = findGroupById(payload.groupId());
        if (group == null) return;

        // Security: only owner can update placement!
        if (!group.isOwner(player.getUUID())) {
            return;
        }

        group.setOrigin(payload.origin());
        group.setRotation(payload.rotation());
        group.setMirror(payload.mirror());
        group.setLocked(payload.locked());

        saveGroup(server, group);
        broadcastGroups(server, group.getDimension());
    }

    public void sendGroupsToPlayer(ServerPlayer player, String dimension) {
        if (player == null) return;
        String dim = (dimension != null && !dimension.isEmpty())
                ? dimension
                : player.level().dimension().identifier().toString();
        List<HologramGroupData> list = getGroupsForDimension(dim);
        ServerPlayNetworking.send(player, new SyncHologramGroupsPayload(dim, list));
    }

    public void broadcastGroups(MinecraftServer server, String dimension) {
        if (server == null || dimension == null) return;
        List<HologramGroupData> list = getGroupsForDimension(dimension);
        SyncHologramGroupsPayload payload = new SyncHologramGroupsPayload(dimension, list);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().identifier().toString().equals(dimension)) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }
}
