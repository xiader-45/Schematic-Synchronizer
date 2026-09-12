package com.schematicsynchronizer.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    public static class FileHashes {
        public final String sha256;
        public final String md5;

        public FileHashes(String sha256, String md5) {
            this.sha256 = sha256;
            this.md5 = md5;
        }
    }

    private static class LocalFileRecord {
        final Path path;
        final long size;
        final long lastModified;
        final String sha256;
        final String md5;

        LocalFileRecord(Path path, long size, long lastModified, String sha256, String md5) {
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.sha256 = sha256;
            this.md5 = md5;
        }
    }

    private final List<ServerSchematicInfo> serverSchematics = new ArrayList<>();
    private final Map<String, ServerSchematicInfo> schematicMap = new ConcurrentHashMap<>();
    private final Set<String> serverDirectories = ConcurrentHashMap.newKeySet();
    private final List<PlayerPlacementInfo> playerPlacements = new ArrayList<>();
    private final Map<String, List<PlayerPlacementInfo>> placementsBySchematic = new ConcurrentHashMap<>();
    private final Map<UUID, String> activePlacementToSchematicId = new ConcurrentHashMap<>();
    private final Set<UUID> placedFromServerPlacements = ConcurrentHashMap.newKeySet();
    private final Set<UUID> sharedPlacements = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> placementTimestamps = new ConcurrentHashMap<>();
    private final Map<Path, LocalFileRecord> localFileRecords = new ConcurrentHashMap<>();
    private final Map<String, Path> hashToLocalPath = new ConcurrentHashMap<>();
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

        cleanLegacyClientStateFiles(getCacheDirectory());
        scanLocalSchematicsAsync();

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
                ClientHologramGroupManager.getInstance().onPlacementModified(placement);
            }

            @Override
            public void onSetRotation(SchematicPlacement placement, Rotation rotation) {
                syncPlacementToServer(placement);
                ClientHologramGroupManager.getInstance().onPlacementModified(placement);
            }

            @Override
            public void onSetMirror(SchematicPlacement placement, Mirror mirror) {
                syncPlacementToServer(placement);
                ClientHologramGroupManager.getInstance().onPlacementModified(placement);
            }

            @Override
            public void onPlacementUpdated(SchematicPlacement placement) {
                syncPlacementToServer(placement);
                ClientHologramGroupManager.getInstance().onPlacementModified(placement);
            }

            @Override
            public void onPlacementReset(SchematicPlacement placement) {
                syncPlacementToServer(placement);
                ClientHologramGroupManager.getInstance().onPlacementModified(placement);
            }
        }, List.of(SchematicPlacementEventFlag.ALL_EVENTS));
    }

    public boolean isAllowedLocalPath(Path path) {
        if (path == null) {
            return false;
        }
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("schematics").toAbsolutePath().normalize();
            Path normPath = path.toAbsolutePath().normalize();

            if (!normPath.startsWith(root)) {
                return true;
            }

            Path rel = root.relativize(normPath);
            int nameCount = rel.getNameCount();
            if (nameCount >= 2) {
                String firstPart = rel.getName(0).toString();
                if (firstPart.equalsIgnoreCase(".server_schematics") ||
                    firstPart.equalsIgnoreCase("server_schematics") ||
                    firstPart.equalsIgnoreCase(".server_cache")) {

                    String serverFolder = rel.getName(1).toString();
                    String currentServerFolder = getServerIdentifier();
                    if (!serverFolder.equalsIgnoreCase(currentServerFolder)) {
                        return false;
                    }
                }
            } else if (nameCount == 1) {
                String firstPart = rel.getName(0).toString();
                if (Files.isRegularFile(normPath) &&
                    (firstPart.equalsIgnoreCase(".server_schematics") ||
                     firstPart.equalsIgnoreCase("server_schematics") ||
                     firstPart.equalsIgnoreCase(".server_cache"))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void scanLocalSchematicsAsync() {
        CompletableFuture.runAsync(this::scanLocalSchematics);
    }

    public synchronized void scanLocalSchematics() {
        Path root = FabricLoader.getInstance().getGameDir().resolve("schematics");
        if (!Files.exists(root)) {
            return;
        }

        Set<Path> currentFiles = new HashSet<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!isAllowedLocalPath(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!isAllowedLocalPath(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".litematic") || name.endsWith(".schematic") ||
                        name.endsWith(".schem") || name.endsWith(".litematica") || name.endsWith(".nbt")) {
                        currentFiles.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
        }

        // Clean up deleted files from local records and map
        this.localFileRecords.keySet().removeIf(p -> !currentFiles.contains(p));
        this.hashToLocalPath.values().removeIf(p -> !currentFiles.contains(p));

        boolean updated = false;
        for (Path path : currentFiles) {
            try {
                long size = Files.size(path);
                long lastModified = Files.getLastModifiedTime(path).toMillis();
                LocalFileRecord record = this.localFileRecords.get(path);

                if (record != null && record.size == size && record.lastModified == lastModified) {
                    if (record.sha256 != null && !record.sha256.isEmpty()) {
                        this.hashToLocalPath.put(record.sha256.toLowerCase(Locale.ROOT), path);
                    }
                    if (record.md5 != null && !record.md5.isEmpty()) {
                        this.hashToLocalPath.put(record.md5.toLowerCase(Locale.ROOT), path);
                    }
                } else {
                    FileHashes hashes = computeFileHashes(path);
                    if (hashes != null) {
                        LocalFileRecord newRecord = new LocalFileRecord(path, size, lastModified, hashes.sha256, hashes.md5);
                        this.localFileRecords.put(path, newRecord);
                        if (hashes.sha256 != null && !hashes.sha256.isEmpty()) {
                            this.hashToLocalPath.put(hashes.sha256.toLowerCase(Locale.ROOT), path);
                        }
                        if (hashes.md5 != null && !hashes.md5.isEmpty()) {
                            this.hashToLocalPath.put(hashes.md5.toLowerCase(Locale.ROOT), path);
                        }
                        updated = true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        ClientHologramGroupManager.getInstance().checkAndDownloadMissingGroupSchematics();
        if (updated) {
            notifyGuiRefresh();
        }
    }

    public Path findLocalFileByHash(String hash, long expectedSize) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        String lowerHash = hash.toLowerCase(Locale.ROOT).trim();
        Path cachedPath = this.hashToLocalPath.get(lowerHash);
        if (cachedPath != null && Files.exists(cachedPath) && isAllowedLocalPath(cachedPath)) {
            try {
                if (expectedSize <= 0 || Files.size(cachedPath) == expectedSize) {
                    return cachedPath;
                }
            } catch (IOException ignored) {
            }
        } else if (cachedPath != null && !isAllowedLocalPath(cachedPath)) {
            this.hashToLocalPath.remove(lowerHash);
        }

        Path root = FabricLoader.getInstance().getGameDir().resolve("schematics");
        if (Files.exists(root)) {
            Path[] result = new Path[1];
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!isAllowedLocalPath(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        if (!isAllowedLocalPath(path)) {
                            return FileVisitResult.CONTINUE;
                        }
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!name.endsWith(".litematic") && !name.endsWith(".schematic") &&
                            !name.endsWith(".schem") && !name.endsWith(".litematica") && !name.endsWith(".nbt")) {
                            return FileVisitResult.CONTINUE;
                        }
                        try {
                            if (expectedSize > 0 && Files.size(path) != expectedSize) {
                                return FileVisitResult.CONTINUE;
                            }
                            Path norm = path.toAbsolutePath().normalize();
                            FileHashes h = computeFileHashes(norm);
                            if (h != null) {
                                LocalFileRecord r = new LocalFileRecord(norm, Files.size(norm), Files.getLastModifiedTime(norm).toMillis(), h.sha256, h.md5);
                                localFileRecords.put(norm, r);
                                if (h.sha256 != null && !h.sha256.isEmpty()) {
                                    hashToLocalPath.put(h.sha256.toLowerCase(Locale.ROOT), norm);
                                }
                                if (h.md5 != null && !h.md5.isEmpty()) {
                                    hashToLocalPath.put(h.md5.toLowerCase(Locale.ROOT), norm);
                                }
                                if (lowerHash.equalsIgnoreCase(h.sha256) || lowerHash.equalsIgnoreCase(h.md5)) {
                                    result[0] = norm;
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception ignored) {
            }

            if (result[0] != null) {
                return result[0];
            }
        }

        return null;
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
        this.updatePlacements(placements);
        this.checkAndCleanPlacements();
        this.scanLocalSchematicsAsync();
        ClientHologramGroupManager.getInstance().checkAndDownloadMissingGroupSchematics();
        this.notifyGuiRefresh();
    }

    public void updatePlacements(List<PlayerPlacementInfo> placements) {
        this.playerPlacements.clear();
        this.placementsBySchematic.clear();
        this.playerPlacements.addAll(placements);
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc != null && mc.getUser() != null) ? mc.getUser().getProfileId() : null;

        for (PlayerPlacementInfo p : placements) {
            this.placementsBySchematic.computeIfAbsent(p.getSchematicId(), k -> new ArrayList<>()).add(p);
            if (p.getPlacementId() != null && p.getTimestamp() > 0) {
                this.placementTimestamps.put(p.getPlacementId(), p.getTimestamp());
            }
            if (p.getPlacementId() != null && myUuid != null && p.getOwnerUuid().equals(myUuid)) {
                this.sharedPlacements.add(p.getPlacementId());
                this.activePlacementToSchematicId.put(p.getPlacementId(), p.getSchematicId());
            }
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
        cleanLegacyClientStateFiles(base);
        return base;
    }

    public Path getLocalFilePath(String id) {
        if (id == null) return null;
        ServerSchematicInfo info = getSchematic(id);
        if (info != null) {
            return getLocalFilePath(info);
        }
        return getCacheDirectory().resolve(id);
    }

    public void downloadSchematic(String id, Consumer<Path> onComplete) {
        if (id == null) return;
        ServerSchematicInfo info = getSchematic(id);
        if (info == null) {
            info = new ServerSchematicInfo(id, id, 0L, "", 0L);
        }
        downloadSchematic(info, onComplete);
    }

    public Path getValidLocalFilePath(ServerSchematicInfo info) {
        if (info == null) {
            return null;
        }
        // 1. Direct server cache path
        Path cachePath = getCacheDirectory().resolve(info.getId());
        if (Files.exists(cachePath) && matchesHashAndSize(cachePath, info)) {
            return cachePath;
        }

        // 2. Any matching file anywhere in schematics directory
        Path localMatch = findLocalFileByHash(info.getHash(), info.getSize());
        if (localMatch != null && Files.exists(localMatch) && isAllowedLocalPath(localMatch)) {
            return localMatch;
        }

        return null;
    }

    public Path getValidLocalFilePath(String id) {
        if (id == null) return null;
        ServerSchematicInfo info = getSchematic(id);
        if (info != null) {
            return getValidLocalFilePath(info);
        }
        Path cachePath = getCacheDirectory().resolve(id);
        if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
            return cachePath;
        }
        return null;
    }

    public boolean isSchematicAvailableLocally(String id) {
        if (id == null) return false;
        ServerSchematicInfo info = getSchematic(id);
        if (info != null) {
            return isDownloaded(info);
        }
        Path cachePath = getCacheDirectory().resolve(id);
        return Files.exists(cachePath) && Files.isRegularFile(cachePath);
    }

    public Path getLocalFilePath(ServerSchematicInfo info) {
        if (info == null) {
            return null;
        }
        // 1. Direct server cache path
        Path cachePath = getCacheDirectory().resolve(info.getId());
        if (Files.exists(cachePath) && matchesHashAndSize(cachePath, info)) {
            return cachePath;
        }

        // 2. Any matching file anywhere in schematics directory
        Path localMatch = findLocalFileByHash(info.getHash(), info.getSize());
        if (localMatch != null && Files.exists(localMatch) && isAllowedLocalPath(localMatch)) {
            return localMatch;
        }

        return cachePath;
    }

    public boolean isDownloaded(ServerSchematicInfo info) {
        if (info == null) {
            return false;
        }
        Path path = getLocalFilePath(info);
        if (path == null || !Files.exists(path) || !isAllowedLocalPath(path)) {
            return false;
        }
        return matchesHashAndSize(path, info);
    }

    private boolean matchesHashAndSize(Path path, ServerSchematicInfo info) {
        if (path == null || !Files.exists(path) || Files.isDirectory(path) || !isAllowedLocalPath(path)) {
            return false;
        }
        try {
            if (info.getSize() > 0 && Files.size(path) != info.getSize()) {
                return false;
            }
            if (info.getHash() != null && !info.getHash().isEmpty()) {
                Path norm = path.toAbsolutePath().normalize();
                LocalFileRecord record = this.localFileRecords.get(norm);
                long mtime = Files.getLastModifiedTime(norm).toMillis();
                long sz = Files.size(norm);
                if (record != null && record.lastModified == mtime && record.size == sz) {
                    return info.getHash().equalsIgnoreCase(record.sha256) || info.getHash().equalsIgnoreCase(record.md5);
                }
                FileHashes hashes = computeFileHashes(norm);
                if (hashes != null) {
                    this.localFileRecords.put(norm, new LocalFileRecord(norm, sz, mtime, hashes.sha256, hashes.md5));
                    if (hashes.sha256 != null && !hashes.sha256.isEmpty()) {
                        this.hashToLocalPath.put(hashes.sha256.toLowerCase(Locale.ROOT), norm);
                    }
                    if (hashes.md5 != null && !hashes.md5.isEmpty()) {
                        this.hashToLocalPath.put(hashes.md5.toLowerCase(Locale.ROOT), norm);
                    }
                    return info.getHash().equalsIgnoreCase(hashes.sha256) || info.getHash().equalsIgnoreCase(hashes.md5);
                }
                return false;
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
        this.scanLocalSchematicsAsync();
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
            Path local = getLocalFilePath(info);
            // If the matching file is outside the server cache directory, copy it to server cache
            Path cachePath = getCacheDirectory().resolve(info.getId());
            if (!cachePath.equals(local) && !Files.exists(cachePath)) {
                try {
                    if (cachePath.getParent() != null && !Files.exists(cachePath.getParent())) {
                        Files.createDirectories(cachePath.getParent());
                    }
                    Files.copy(local, cachePath, StandardCopyOption.REPLACE_EXISTING);
                    local = cachePath;
                } catch (Exception ignored) {
                }
            }
            if (onComplete != null) {
                onComplete.accept(local);
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

                Path target = getCacheDirectory().resolve(id);
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                    fos.write(baos.toByteArray());
                }

                this.activeDownloads.remove(id);
                this.downloadChunks.remove(id);
                this.expectedChunks.remove(id);

                scanLocalSchematicsAsync();

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
            scanLocalSchematicsAsync();
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
            long now = System.currentTimeMillis();
            this.placementTimestamps.put(placement.getHashId(), now);
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
        if (id != null) {
            for (PlayerPlacementInfo p : this.playerPlacements) {
                if (p.getPlacementId().equals(id) && this.schematicMap.containsKey(p.getSchematicId())) {
                    this.placedFromServerPlacements.add(id);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPlacementShared(SchematicPlacement placement) {
        if (placement == null) return false;
        UUID id = placement.getHashId();
        if (id != null && this.sharedPlacements.contains(id)) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && id != null) {
            UUID myUuid = mc.getUser().getProfileId();
            for (PlayerPlacementInfo p : this.playerPlacements) {
                if (p.getOwnerUuid().equals(myUuid) && p.getPlacementId().equals(id)) {
                    this.sharedPlacements.add(id);
                    return true;
                }
            }
        }
        return false;
    }

    public void setPlacementShared(SchematicPlacement placement, boolean shared) {
        if (placement == null) return;
        UUID id = placement.getHashId();
        if (id == null) return;

        if (shared) {
            this.sharedPlacements.add(id);
            sharePlacementToServer(placement);
        } else {
            this.sharedPlacements.remove(id);
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

    private static void cleanLegacyClientStateFiles(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.deleteIfExists(dir.resolve("shared_placements.json"));
            Files.deleteIfExists(dir.resolve("placed_from_server.json"));
            Files.deleteIfExists(dir.resolve("placement_timestamps.json"));
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
        String dim = mc.level.dimension().identifier().toString();
        String rot = placement.getRotation() != null ? placement.getRotation().name() : "NONE";
        String mir = placement.getMirror() != null ? placement.getMirror().name() : "NONE";

        // Avoid re-publishing if this placement is already synced on the server identically
        for (PlayerPlacementInfo existing : this.playerPlacements) {
            if (existing.getOwnerUuid().equals(mc.getUser().getProfileId())
                    && existing.getSchematicId().equalsIgnoreCase(schematicId)
                    && existing.getPos().equals(placement.getOrigin())
                    && existing.getDimension().equalsIgnoreCase(dim)
                    && Objects.equals(existing.getRotation(), rot)
                    && Objects.equals(existing.getMirror(), mir)) {
                return;
            }
        }

        long timestamp = this.placementTimestamps.getOrDefault(placement.getHashId(), 0L);
        if (timestamp <= 0) {
            for (PlayerPlacementInfo p : this.playerPlacements) {
                if (p.getPlacementId().equals(placement.getHashId()) ||
                    (p.getOwnerUuid().equals(mc.getUser().getProfileId()) && p.getSchematicId().equalsIgnoreCase(schematicId))) {
                    timestamp = p.getTimestamp();
                    break;
                }
            }
        }
        if (timestamp <= 0) {
            timestamp = System.currentTimeMillis();
            this.placementTimestamps.put(placement.getHashId(), timestamp);
        }

        if (ClientPlayNetworking.canSend(PublishPlacementPayload.TYPE)) {
            PlayerPlacementInfo info = new PlayerPlacementInfo(
                    placement.getHashId(),
                    schematicId,
                    mc.getUser().getName(),
                    mc.getUser().getProfileId(),
                    placement.getOrigin(),
                    dim,
                    rot,
                    mir,
                    timestamp
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
            this.placementTimestamps.remove(hashId);
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

    public static FileHashes computeFileHashes(Path path) {
        if (path == null || !Files.exists(path) || Files.isDirectory(path)) {
            return null;
        }
        try (InputStream fis = Files.newInputStream(path)) {
            MessageDigest shaDigest = MessageDigest.getInstance("SHA-256");
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                shaDigest.update(buffer, 0, read);
                md5Digest.update(buffer, 0, read);
            }
            byte[] shaBytes = shaDigest.digest();
            byte[] md5Bytes = md5Digest.digest();
            StringBuilder sbSha = new StringBuilder();
            for (byte b : shaBytes) {
                sbSha.append(String.format("%02x", b));
            }
            StringBuilder sbMd5 = new StringBuilder();
            for (byte b : md5Bytes) {
                sbMd5.append(String.format("%02x", b));
            }
            return new FileHashes(sbSha.toString(), sbMd5.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static String computeHash(Path path) {
        FileHashes hashes = computeFileHashes(path);
        return hashes != null ? hashes.sha256 : "";
    }

    public static String computeHash(Path path, String expectedHash) {
        FileHashes hashes = computeFileHashes(path);
        if (hashes == null) {
            return "";
        }
        if (expectedHash != null && expectedHash.length() == 32) {
            return hashes.md5;
        }
        return hashes.sha256;
    }
}
