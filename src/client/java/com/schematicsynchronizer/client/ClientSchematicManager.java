package com.schematicsynchronizer.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.schematicsynchronizer.client.gui.GuiServerSchematicsList;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import com.schematicsynchronizer.network.*;
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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
    private final Set<String> serverDirectories = ConcurrentHashMap.newKeySet();
    private final List<PlayerPlacementInfo> playerPlacements = new ArrayList<>();
    private final Map<String, List<PlayerPlacementInfo>> placementsBySchematic = new ConcurrentHashMap<>();
    private final Map<UUID, String> activePlacementToSchematicId = new ConcurrentHashMap<>();
    private final Set<UUID> placedFromServerPlacements = ConcurrentHashMap.newKeySet();
    private final Set<UUID> sharedPlacements = ConcurrentHashMap.newKeySet();
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

    public Set<String> getServerDirectories() {
        return Collections.unmodifiableSet(this.serverDirectories);
    }

    public void init() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        loadPlacementStates();

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

    public void updateCatalog(List<ServerSchematicInfo> schematics, List<PlayerPlacementInfo> placements, String serverDir, List<String> directories) {
        if (serverDir != null && !serverDir.isEmpty()) {
            this.lastServerDirectory = serverDir;
        }
        this.serverSchematics.clear();
        this.schematicMap.clear();
        this.serverDirectories.clear();
        if (directories != null) {
            this.serverDirectories.addAll(directories);
        }
        this.serverSchematics.addAll(schematics);
        for (ServerSchematicInfo s : schematics) {
            this.schematicMap.put(s.getId(), s);
        }
        this.loadPlacementStates();
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

    public ServerSchematicInfo getSchematic(String id) {
        return id != null ? this.schematicMap.get(id) : null;
    }

    public List<PlayerPlacementInfo> getAllPlacements() {
        return Collections.unmodifiableList(this.playerPlacements);
    }

    public List<PlayerPlacementInfo> getPlacementsForSchematic(String schematicId) {
        List<PlayerPlacementInfo> list = this.placementsBySchematic.get(schematicId);
        return list != null ? list : Collections.emptyList();
    }

    public String getServerIdentifier() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isEmpty()) {
            return sanitizeFolderName(mc.getCurrentServer().ip);
        }
        if (mc.hasSingleplayerServer()) {
            return "singleplayer";
        }
        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            SocketAddress addr = mc.getConnection().getConnection().getRemoteAddress();
            if (addr instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                int port = inet.getPort();
                return sanitizeFolderName(port == 25565 ? host : (host + "_" + port));
            } else if (addr != null) {
                return sanitizeFolderName(addr.toString());
            }
        }
        return "default";
    }

    private static String sanitizeFolderName(String name) {
        if (name == null || name.isEmpty()) return "default";
        String clean = name.trim().replaceAll("[:\\\\/*?\"<>|]", "_");
        while (clean.endsWith(".") || clean.endsWith(" ")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.isEmpty() ? "default" : clean;
    }

    private static void migrateFolder(Path source, Path target) {
        if (source == null || !Files.exists(source) || !Files.isDirectory(source)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                if (Files.isRegularFile(src)) {
                    try {
                        Path rel = source.relativize(src);
                        Path dest = target.resolve(rel);
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

    public Path getCacheDirectory() {
        String serverFolder = getServerIdentifier();
        Path base = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve(".server_schematics").resolve(serverFolder);
        try {
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
            // Migrate legacy .server_cache if it exists
            Path legacyCache = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve(".server_cache");
            migrateFolder(legacyCache, base);

            // Migrate legacy schematics/server
            Path legacyServer = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve("server");
            if (Files.exists(legacyServer)) {
                Path legacyServerSub = legacyServer.resolve(serverFolder);
                if (Files.exists(legacyServerSub)) {
                    migrateFolder(legacyServerSub, base);
                }
                migrateFolder(legacyServer, base);
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
                if (isPlacedFromServer(p) || isPlacementShared(p)) {
                    String id = getSchematicIdForPlacement(p);
                    if (id != null) {
                        activeSchematicIds.add(id);
                    }
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

    public void uploadFile(Path localFile, String targetServerId) {
        if (localFile == null || !Files.exists(localFile) || Files.isDirectory(localFile)) {
            return;
        }
        if (!ClientPlayNetworking.canSend(UploadSchematicChunkPayload.TYPE)) {
            return;
        }
        try {
            byte[] fileBytes = Files.readAllBytes(localFile);
            int chunkSize = 16384; // 16 KB chunks
            int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
            if (totalChunks == 0) totalChunks = 1;

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileBytes.length);
                byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);

                UploadSchematicChunkPayload payload = new UploadSchematicChunkPayload(
                        targetServerId,
                        i,
                        totalChunks,
                        chunk
                );
                ClientPlayNetworking.send(payload);
            }
        } catch (IOException ignored) {
        }
    }

    public void createServerDirectory(String directoryPath) {
        if (ClientPlayNetworking.canSend(CreateServerDirectoryPayload.TYPE)) {
            ClientPlayNetworking.send(new CreateServerDirectoryPayload(directoryPath));
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
            this.placedFromServerPlacements.add(placement.getHashId());
            savePlacementStates();
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
            syncPlacementToServer(placement);
        } catch (Exception ignored) {
        }
    }

    public boolean isPlacedFromServer(SchematicPlacement placement) {
        if (placement == null) return false;
        UUID id = placement.getHashId();
        if (id != null && this.placedFromServerPlacements.contains(id)) {
            return true;
        }
        if (placement.getSchematic() != null && placement.getSchematic().getFile() != null) {
            try {
                Path file = placement.getSchematic().getFile().toAbsolutePath().normalize();
                Path cacheDir = getCacheDirectory().toAbsolutePath().normalize();
                if (file.startsWith(cacheDir)) {
                    return true;
                }
                String pathStr = file.toString().replace('\\', '/');
                if (pathStr.contains("/.server_schematics/")) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean isPlacementShared(SchematicPlacement placement) {
        if (placement == null) return false;
        UUID id = placement.getHashId();
        return id != null && this.sharedPlacements.contains(id);
    }

    public void setPlacementShared(SchematicPlacement placement, boolean shared) {
        if (placement == null) return;
        UUID id = placement.getHashId();
        if (id == null) return;

        if (shared) {
            this.sharedPlacements.add(id);
            savePlacementStates();
            sharePlacementToServer(placement);
        } else {
            this.sharedPlacements.remove(id);
            savePlacementStates();
            unsharePlacementFromServer(placement);
        }
    }

    public void sharePlacementToServer(SchematicPlacement placement) {
        if (placement == null) return;

        Path localFile = placement.getSchematic() != null ? placement.getSchematic().getFile() : null;
        String fileName = null;
        if (localFile != null && Files.exists(localFile)) {
            fileName = localFile.getFileName().toString();
        } else if (placement.getSchematic() != null) {
            String name = placement.getName();
            if (!name.endsWith(".litematic")) {
                name = name + ".litematic";
            }
            Path exportDir = getCacheDirectory().resolve("temp_shared");
            try {
                Files.createDirectories(exportDir);
                placement.getSchematic().writeToFile(exportDir, name, true);
                localFile = exportDir.resolve(name);
                fileName = name;
            } catch (Exception ignored) {
            }
        }

        if (fileName == null) {
            fileName = placement.getName() + ".litematic";
        }

        this.activePlacementToSchematicId.put(placement.getHashId(), fileName);

        if (localFile != null && Files.exists(localFile) && !this.schematicMap.containsKey(fileName)) {
            uploadFile(localFile, fileName);
        }

        syncPlacementToServer(placement);
    }

    public void unsharePlacementFromServer(SchematicPlacement placement) {
        if (placement == null) return;
        UUID id = placement.getHashId();
        String schematicId = getSchematicIdForPlacement(placement);
        if (id != null) {
            this.activePlacementToSchematicId.remove(id);
        }
        if (ClientPlayNetworking.canSend(RemovePlacementPayload.TYPE)) {
            ClientPlayNetworking.send(new RemovePlacementPayload(id, schematicId));
        }
    }

    private void loadPlacementStates() {
        try {
            Path cacheDir = getCacheDirectory();
            loadUuidSet(cacheDir.resolve("shared_placements.json"), this.sharedPlacements);
            loadUuidSet(cacheDir.resolve("placed_from_server.json"), this.placedFromServerPlacements);
        } catch (Exception ignored) {
        }
    }

    private void savePlacementStates() {
        try {
            Path cacheDir = getCacheDirectory();
            saveUuidSet(cacheDir.resolve("shared_placements.json"), this.sharedPlacements);
            saveUuidSet(cacheDir.resolve("placed_from_server.json"), this.placedFromServerPlacements);
        } catch (Exception ignored) {
        }
    }

    private void loadUuidSet(Path file, Set<UUID> set) {
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el != null && el.isJsonArray()) {
                set.clear();
                for (JsonElement item : el.getAsJsonArray()) {
                    try {
                        set.add(UUID.fromString(item.getAsString()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveUuidSet(Path file, Set<UUID> set) {
        try {
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            JsonArray arr = new JsonArray();
            for (UUID id : set) {
                arr.add(id.toString());
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                new Gson().toJson(arr, writer);
            }
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
        if (isPlacementShared(placement)) {
            if (placement.getSchematic() != null && placement.getSchematic().getFile() != null) {
                String name = placement.getSchematic().getFile().getFileName().toString();
                if (hashId != null) {
                    this.activePlacementToSchematicId.put(hashId, name);
                }
                return name;
            }
        }
        return null;
    }

    public void syncPlacementToServer(SchematicPlacement placement) {
        if (placement == null) {
            return;
        }
        if (!isPlacedFromServer(placement) && !isPlacementShared(placement)) {
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
        boolean wasServer = isPlacedFromServer(placement);
        boolean wasShared = isPlacementShared(placement);
        if (hashId != null) {
            this.placedFromServerPlacements.remove(hashId);
            this.sharedPlacements.remove(hashId);
            savePlacementStates();
        }
        if (wasServer || wasShared) {
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
