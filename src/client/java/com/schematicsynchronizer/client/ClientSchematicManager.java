package com.schematicsynchronizer.client;

import com.schematicsynchronizer.client.gui.GuiServerSchematicsList;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import com.schematicsynchronizer.network.DownloadSchematicRequestPayload;
import com.schematicsynchronizer.network.PublishPlacementPayload;
import com.schematicsynchronizer.network.RemovePlacementPayload;
import com.schematicsynchronizer.network.RequestSchematicsPayload;
import com.schematicsynchronizer.network.SchematicChunkPayload;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.interfaces.ISchematicPlacementEventListener;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementEventHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementEventFlag;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.util.InfoUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ClientSchematicManager {
    private static final ClientSchematicManager INSTANCE = new ClientSchematicManager();
    private static final IMessageConsumer DUMMY_CONSUMER = new IMessageConsumer() {
        @Override
        public void addMessage(Message.MessageType type, String messageKey, Object... args) {
        }

        @Override
        public void addMessage(Message.MessageType type, int displayTime, String messageKey, Object... args) {
        }
    };

    private final List<ServerSchematicInfo> serverSchematics = new ArrayList<>();
    private final Map<String, ServerSchematicInfo> schematicMap = new ConcurrentHashMap<>();
    private final List<PlayerPlacementInfo> playerPlacements = new ArrayList<>();
    private final Map<String, List<PlayerPlacementInfo>> placementsBySchematic = new ConcurrentHashMap<>();
    private final Map<UUID, String> activePlacementToSchematicId = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, byte[]>> downloadChunks = new ConcurrentHashMap<>();
    private final Map<String, Integer> expectedChunks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Path>>> downloadCallbacks = new ConcurrentHashMap<>();
    private final Set<String> activeDownloads = ConcurrentHashMap.newKeySet();
    private String lastServerDirectory = "";
    private boolean initialized = false;

    public static ClientSchematicManager getInstance() {
        return INSTANCE;
    }

    public String getLastServerDirectory() {
        return this.lastServerDirectory;
    }

    public void init() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        SchematicPlacementEventHandler.getInstance().registerSchematicPlacementEventListener(new ISchematicPlacementEventListener() {
            @Override
            public void onPlacementAdded(SchematicPlacement placement) {
                syncPlacementToServer(placement);
            }

            @Override
            public void onPlacementRemoved(SchematicPlacement placement) {
                removePlacementFromServer(placement);
            }

            @Override
            public void onSetOrigin(SchematicPlacement placement, BlockPos origin) {
                syncPlacementToServer(placement);
            }

            @Override
            public void onSetRotation(SchematicPlacement placement, Rotation rotation) {
                syncPlacementToServer(placement);
            }

            @Override
            public void onSetMirror(SchematicPlacement placement, Mirror mirror) {
                syncPlacementToServer(placement);
            }

            @Override
            public void onPlacementUpdated(SchematicPlacement placement) {
                syncPlacementToServer(placement);
            }

            @Override
            public void onPlacementReset(SchematicPlacement placement) {
                syncPlacementToServer(placement);
            }
        }, List.of(SchematicPlacementEventFlag.ALL_EVENTS));
    }

    public void updateCatalog(List<ServerSchematicInfo> schematics, List<PlayerPlacementInfo> placements, String serverDir) {
        if (serverDir != null && !serverDir.isEmpty()) {
            this.lastServerDirectory = serverDir;
        }
        this.serverSchematics.clear();
        this.schematicMap.clear();
        this.serverSchematics.addAll(schematics);
        for (ServerSchematicInfo s : schematics) {
            this.schematicMap.put(s.getId(), s);
        }
        this.updatePlacements(placements);
        this.checkAndCleanPlacements();
        this.notifyGuiRefresh();
    }

    public void updatePlacements(List<PlayerPlacementInfo> placements) {
        this.playerPlacements.clear();
        this.placementsBySchematic.clear();
        this.playerPlacements.addAll(placements);
        for (PlayerPlacementInfo p : placements) {
            this.placementsBySchematic.computeIfAbsent(p.getSchematicId(), k -> new ArrayList<>()).add(p);
        }
        this.notifyGuiRefresh();
    }

    public void notifyGuiRefresh() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui != null && client.gui.screen() instanceof GuiServerSchematicsList gui) {
                gui.refreshList();
            }
        });
    }

    public List<ServerSchematicInfo> getServerSchematics() {
        return Collections.unmodifiableList(this.serverSchematics);
    }

    public List<PlayerPlacementInfo> getPlacementsForSchematic(String schematicId) {
        List<PlayerPlacementInfo> list = this.placementsBySchematic.get(schematicId);
        return list != null ? list : Collections.emptyList();
    }

    public Path getCacheDirectory() {
        Path base = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve("server");
        try {
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
            Path legacy = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve(".server_cache");
            if (Files.exists(legacy) && Files.isDirectory(legacy)) {
                try (Stream<Path> stream = Files.walk(legacy)) {
                    stream.forEach(src -> {
                        if (Files.isRegularFile(src)) {
                            Path rel = legacy.relativize(src);
                            Path dest = base.resolve(rel);
                            try {
                                if (dest.getParent() != null && !Files.exists(dest.getParent())) {
                                    Files.createDirectories(dest.getParent());
                                }
                                if (!Files.exists(dest)) {
                                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return base;
    }

    public Path getLocalFilePath(ServerSchematicInfo info) {
        return getCacheDirectory().resolve(info.getId());
    }

    public boolean isDownloaded(ServerSchematicInfo info) {
        Path path = getLocalFilePath(info);
        if (!Files.exists(path)) {
            return false;
        }
        try {
            if (Files.size(path) != info.getSize()) {
                return false;
            }
            if (info.getHash() != null && !info.getHash().isEmpty()) {
                String localHash = computeHash(path);
                return info.getHash().equalsIgnoreCase(localHash);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isDownloading(ServerSchematicInfo info) {
        return this.activeDownloads.contains(info.getId());
    }

    public void requestRefresh() {
        this.checkAndCleanPlacements();
        try {
            ClientPlayNetworking.send(new RequestSchematicsPayload());
        } catch (Throwable ignored) {
        }
    }

    public void checkAndCleanPlacements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        UUID myUuid = mc.getUser().getProfileId();
        List<SchematicPlacement> currentPlacements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        Set<String> activeSchematicIds = new HashSet<>();
        if (currentPlacements != null) {
            for (SchematicPlacement p : currentPlacements) {
                String id = getSchematicIdForPlacement(p);
                if (id != null) {
                    activeSchematicIds.add(id);
                }
            }
        }

        for (PlayerPlacementInfo p : new ArrayList<>(this.playerPlacements)) {
            if (p.getOwnerUuid().equals(myUuid)) {
                if (!activeSchematicIds.contains(p.getSchematicId())) {
                    if (ClientPlayNetworking.canSend(RemovePlacementPayload.TYPE)) {
                        ClientPlayNetworking.send(new RemovePlacementPayload(p.getPlacementId(), p.getSchematicId()));
                    }
                }
            }
        }
    }

    public void downloadSchematic(ServerSchematicInfo info, Consumer<Path> onComplete) {
        if (isDownloaded(info)) {
            if (onComplete != null) {
                onComplete.accept(getLocalFilePath(info));
            }
            return;
        }

        if (onComplete != null) {
            this.downloadCallbacks.computeIfAbsent(info.getId(), k -> new ArrayList<>()).add(onComplete);
        }

        if (!this.activeDownloads.contains(info.getId())) {
            this.activeDownloads.add(info.getId());
            this.downloadChunks.put(info.getId(), new ConcurrentHashMap<>());
            if (ClientPlayNetworking.canSend(DownloadSchematicRequestPayload.TYPE)) {
                ClientPlayNetworking.send(new DownloadSchematicRequestPayload(info.getId()));
            }
        }
    }

    public void handleChunk(SchematicChunkPayload payload) {
        String id = payload.schematicId();
        int chunkIdx = payload.chunkIndex();
        int total = payload.totalChunks();
        byte[] data = payload.data();

        Map<Integer, byte[]> chunks = this.downloadChunks.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        this.expectedChunks.put(id, total);
        chunks.put(chunkIdx, data);

        if (chunks.size() == total) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int i = 0; i < total; i++) {
                    byte[] part = chunks.get(i);
                    if (part != null) {
                        baos.write(part);
                    }
                }

                ServerSchematicInfo info = this.schematicMap.get(id);
                Path target = (info != null) ? getLocalFilePath(info) : getCacheDirectory().resolve(id);
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                    fos.write(baos.toByteArray());
                }

                this.activeDownloads.remove(id);
                this.downloadChunks.remove(id);
                this.expectedChunks.remove(id);

                List<Consumer<Path>> callbacks = this.downloadCallbacks.remove(id);
                if (callbacks != null) {
                    for (Consumer<Path> cb : callbacks) {
                        try {
                            cb.accept(target);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                notifyGuiRefresh();
            } catch (IOException ignored) {
            }
        }
    }

    public static LitematicaSchematic loadSchematicFromFile(Path path) {
        if (path == null || !Files.exists(path, new LinkOption[0])) {
            return null;
        }
        String fileName = path.getFileName().toString();
        Path dir = path.getParent();
        String lower = fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".schematic")) {
                return WorldUtils.convertSchematicaSchematicToLitematicaSchematic(dir, fileName, false, null);
            } else if (lower.endsWith(".schem")) {
                return WorldUtils.convertSpongeSchematicToLitematicaSchematic(dir, fileName);
            } else if (lower.endsWith(".nbt")) {
                return WorldUtils.convertStructureToLitematicaSchematic(dir, fileName);
            } else {
                return LitematicaSchematic.createFromFile(dir, fileName);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void loadSchematicInMemory(ServerSchematicInfo info) {
        Minecraft client = Minecraft.getInstance();
        downloadSchematic(info, path -> {
            client.execute(() -> {
                try {
                    LitematicaSchematic schematic = loadSchematicFromFile(path);
                    if (schematic != null) {
                        SchematicHolder.getInstance().addSchematic(schematic, true);
                    }
                } catch (Exception ignored) {
                }
            });
        });
    }

    public void loadAndPlaceAtPlayer(ServerSchematicInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        BlockPos pos = client.player.blockPosition();
        downloadSchematic(info, path -> {
            client.execute(() -> {
                placePlacementInternal(info, path, pos, Rotation.NONE, Mirror.NONE);
            });
        });
    }

    public void placeLikePlayer(PlayerPlacementInfo placement) {
        ServerSchematicInfo info = this.schematicMap.get(placement.getSchematicId());
        if (info == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Rotation rot = Rotation.NONE;
        Mirror mirror = Mirror.NONE;
        try {
            if (placement.getRotation() != null) {
                rot = Rotation.valueOf(placement.getRotation());
            }
        } catch (Throwable ignored) {
        }
        try {
            if (placement.getMirror() != null) {
                mirror = Mirror.valueOf(placement.getMirror());
            }
        } catch (Throwable ignored) {
        }

        final Rotation finalRot = rot;
        final Mirror finalMirror = mirror;
        downloadSchematic(info, path -> {
            client.execute(() -> {
                placePlacementInternal(info, path, placement.getPos(), finalRot, finalMirror);
            });
        });
    }

    public void openMaterialList(ServerSchematicInfo info, Screen parent) {
        Minecraft client = Minecraft.getInstance();
        downloadSchematic(info, path -> {
            client.execute(() -> {
                try {
                    LitematicaSchematic schematic = loadSchematicFromFile(path);
                    if (schematic != null) {
                        MaterialListSchematic matList = new MaterialListSchematic(schematic, true);
                        DataManager.setMaterialList(matList);
                        GuiMaterialList gui = new GuiMaterialList(matList);
                        gui.setParent(parent);
                        GuiBase.openGui(gui);
                    }
                } catch (Exception ignored) {
                }
            });
        });
    }

    private void placePlacementInternal(ServerSchematicInfo info, Path path, BlockPos pos, Rotation rotation, Mirror mirror) {
        try {
            LitematicaSchematic schematic = loadSchematicFromFile(path);
            if (schematic == null) {
                return;
            }
            UUID placementId = UUID.randomUUID();
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, pos, info.getName(), true, true, placementId);
            if (rotation != null) {
                placement.setRotation(rotation, DUMMY_CONSUMER);
            }
            if (mirror != null) {
                placement.setMirror(mirror, DUMMY_CONSUMER);
            }
            this.activePlacementToSchematicId.put(placement.getHashId(), info.getId());
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
            syncPlacementToServer(placement);
        } catch (Exception ignored) {
        }
    }

    public String getSchematicIdForPlacement(SchematicPlacement placement) {
        if (placement == null) {
            return null;
        }
        UUID hashId = placement.getHashId();
        if (hashId != null) {
            String knownId = this.activePlacementToSchematicId.get(hashId);
            if (knownId != null) {
                return knownId;
            }
        }
        if (placement.getSchematic() != null && placement.getSchematic().getFile() != null) {
            try {
                Path cacheDir = getCacheDirectory().toAbsolutePath().normalize();
                Path file = placement.getSchematic().getFile().toAbsolutePath().normalize();
                if (file.startsWith(cacheDir)) {
                    String rel = cacheDir.relativize(file).toString().replace('\\', '/');
                    if (hashId != null) {
                        this.activePlacementToSchematicId.put(hashId, rel);
                    }
                    return rel;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public void syncPlacementToServer(SchematicPlacement placement) {
        if (placement == null) {
            return;
        }
        String schematicId = getSchematicIdForPlacement(placement);
        if (schematicId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (ClientPlayNetworking.canSend(PublishPlacementPayload.TYPE)) {
            String dim = mc.level.dimension().identifier().toString();
            PlayerPlacementInfo info = new PlayerPlacementInfo(
                    placement.getHashId(),
                    schematicId,
                    mc.getUser().getName(),
                    mc.getUser().getProfileId(),
                    placement.getOrigin(),
                    dim,
                    placement.getRotation() != null ? placement.getRotation().name() : "NONE",
                    placement.getMirror() != null ? placement.getMirror().name() : "NONE",
                    System.currentTimeMillis()
            );
            ClientPlayNetworking.send(new PublishPlacementPayload(info));
        }
    }

    public void removePlacementFromServer(SchematicPlacement placement) {
        if (placement == null) {
            return;
        }
        UUID hashId = placement.getHashId();
        String schematicId = getSchematicIdForPlacement(placement);
        if (hashId != null) {
            this.activePlacementToSchematicId.remove(hashId);
        }
        if (hashId != null || (schematicId != null && !schematicId.isEmpty())) {
            if (ClientPlayNetworking.canSend(RemovePlacementPayload.TYPE)) {
                ClientPlayNetworking.send(new RemovePlacementPayload(hashId, schematicId));
            }
        }
    }

    private void sendClientChat(String message) {
        InfoUtils.sendVanillaMessage(Component.literal(message));
    }

    private static String computeHash(Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] md5 = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
