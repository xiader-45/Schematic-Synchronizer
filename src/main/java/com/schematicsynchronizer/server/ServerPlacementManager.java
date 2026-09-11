package com.schematicsynchronizer.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.network.SyncPlacementsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlacementManager {
    private static final ServerPlacementManager INSTANCE = new ServerPlacementManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerPlacementInfo> activePlacements = new ConcurrentHashMap<>();

    public static ServerPlacementManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("schematic-synchronizer").resolve("placements.json");
    }

    public synchronized void load() {
        Path path = getStoragePath();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonArray()) {
                activePlacements.clear();
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        try {
                            UUID placementId = UUID.fromString(obj.get("placementId").getAsString());
                            String schematicId = obj.get("schematicId").getAsString();
                            String ownerName = obj.get("ownerName").getAsString();
                            UUID ownerUuid = UUID.fromString(obj.get("ownerUuid").getAsString());
                            int x = obj.get("x").getAsInt();
                            int y = obj.get("y").getAsInt();
                            int z = obj.get("z").getAsInt();
                            String dimension = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
                            String rotation = obj.has("rotation") ? obj.get("rotation").getAsString() : "NONE";
                            String mirror = obj.has("mirror") ? obj.get("mirror").getAsString() : "NONE";
                            long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : System.currentTimeMillis();

                            PlayerPlacementInfo info = new PlayerPlacementInfo(
                                    placementId, schematicId, ownerName, ownerUuid,
                                    new BlockPos(x, y, z), dimension, rotation, mirror, timestamp
                            );
                            activePlacements.put(placementId, info);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        Path path = getStoragePath();
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            JsonArray arr = new JsonArray();
            for (PlayerPlacementInfo p : activePlacements.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("placementId", p.getPlacementId().toString());
                obj.addProperty("schematicId", p.getSchematicId());
                obj.addProperty("ownerName", p.getOwnerName());
                obj.addProperty("ownerUuid", p.getOwnerUuid().toString());
                obj.addProperty("x", p.getPos().getX());
                obj.addProperty("y", p.getPos().getY());
                obj.addProperty("z", p.getPos().getZ());
                obj.addProperty("dimension", p.getDimension());
                obj.addProperty("rotation", p.getRotation());
                obj.addProperty("mirror", p.getMirror());
                obj.addProperty("timestamp", p.getTimestamp());
                arr.add(obj);
            }

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(arr, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public void addOrUpdatePlacement(MinecraftServer server, PlayerPlacementInfo placement) {
        if (placement == null) return;
        
        // Remove old placement by same player and schematic if exists
        activePlacements.entrySet().removeIf(entry ->
                entry.getValue().getOwnerUuid().equals(placement.getOwnerUuid()) &&
                entry.getValue().getSchematicId().equalsIgnoreCase(placement.getSchematicId()) &&
                !entry.getKey().equals(placement.getPlacementId()));

        activePlacements.put(placement.getPlacementId(), placement);
        save();
        broadcastPlacements(server);
    }

    public void removePlacement(MinecraftServer server, UUID ownerUuid, UUID placementId, String schematicId) {
        boolean removed = false;
        
        if (placementId != null && activePlacements.containsKey(placementId)) {
            PlayerPlacementInfo info = activePlacements.get(placementId);
            if (info.getOwnerUuid().equals(ownerUuid)) { // Security: only owner can remove
                activePlacements.remove(placementId);
                removed = true;
            }
        } 
        
        if (!removed && ownerUuid != null && schematicId != null && !schematicId.isEmpty()) {
            removed = activePlacements.entrySet().removeIf(entry ->
                    entry.getValue().getOwnerUuid().equals(ownerUuid) &&
                    entry.getValue().getSchematicId().equalsIgnoreCase(schematicId));
        }
        
        if (!removed && placementId == null && (schematicId == null || schematicId.isEmpty()) && ownerUuid != null) {
            removed = activePlacements.entrySet().removeIf(entry -> entry.getValue().getOwnerUuid().equals(ownerUuid));
        }

        if (removed) {
            save();
            if (server != null) {
                broadcastPlacements(server);
            }
        }
    }

    public List<PlayerPlacementInfo> getAllPlacements() {
        return new ArrayList<>(activePlacements.values());
    }

    public void broadcastPlacements(MinecraftServer server) {
        if (server == null) return;
        List<PlayerPlacementInfo> placements = getAllPlacements();
        SyncPlacementsPayload payload = new SyncPlacementsPayload(placements);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
