package com.schematicsynchronizer.server;

import com.schematicsynchronizer.data.ServerSchematicInfo;
import com.schematicsynchronizer.network.SchematicChunkPayload;
import com.schematicsynchronizer.network.SchematicListPayload;
import com.schematicsynchronizer.network.UploadSchematicChunkPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ServerSchematicManager {
    private static final ServerSchematicManager INSTANCE = new ServerSchematicManager();
    public static final int CHUNK_SIZE = 16384; // 16 KB chunks

    private Path schematicsDir;
    private final Map<String, ServerSchematicInfo> schematics = new ConcurrentHashMap<>();
    private final Set<String> serverDirectories = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<Integer, byte[]>> uploadChunks = new ConcurrentHashMap<>();

    public static ServerSchematicManager getInstance() {
        return INSTANCE;
    }

    public Path getSchematicsDir() {
        if (schematicsDir == null) {
            determineSchematicsDir(null);
        }
        return schematicsDir;
    }

    private synchronized void determineSchematicsDir(MinecraftServer server) {
        Path base = null;
        if (server != null && server.getServerDirectory() != null) {
            base = server.getServerDirectory();
        }
        if (base == null) {
            base = FabricLoader.getInstance().getGameDir();
        }

        String custom = ServerConfig.getInstance().getSchematicsDirectory();
        if (custom != null && !custom.trim().isEmpty()) {
            Path customPath = Paths.get(custom.trim());
            if (customPath.isAbsolute()) {
                schematicsDir = customPath.normalize();
                return;
            } else {
                schematicsDir = base.resolve(customPath).toAbsolutePath().normalize();
                return;
            }
        }

        Path cand1 = base.resolve("schematics").toAbsolutePath().normalize();
        Path cand2 = base.resolve("Schematics").toAbsolutePath().normalize();
        Path cand3 = Paths.get("schematics").toAbsolutePath().normalize();
        Path cand4 = Paths.get("Schematics").toAbsolutePath().normalize();

        if (Files.exists(cand1)) {
            schematicsDir = cand1;
        } else if (Files.exists(cand2)) {
            schematicsDir = cand2;
        } else if (Files.exists(cand3)) {
            schematicsDir = cand3;
        } else if (Files.exists(cand4)) {
            schematicsDir = cand4;
        } else {
            schematicsDir = cand1;
        }
    }

    public void init(MinecraftServer server) {
        determineSchematicsDir(server);

        try {
            if (!Files.exists(schematicsDir)) {
                Files.createDirectories(schematicsDir);
            }
            Path readme = schematicsDir.resolve("README.txt");
            if (Files.exists(readme)) {
                Files.deleteIfExists(readme);
            }
        } catch (IOException ignored) {
        }

        scanSchematics();
    }

    public synchronized void scanSchematics() {
        if (schematicsDir == null) {
            determineSchematicsDir(null);
        }

        try {
            if (!Files.exists(schematicsDir)) {
                Files.createDirectories(schematicsDir);
            }
        } catch (IOException ignored) {
        }

        schematics.clear();
        serverDirectories.clear();

        if (!Files.exists(schematicsDir)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(schematicsDir, FileVisitOption.FOLLOW_LINKS)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    if (!path.equals(schematicsDir)) {
                        String rel = schematicsDir.relativize(path).toString().replace('\\', '/');
                        if (!rel.isEmpty() && !rel.startsWith(".")) {
                            serverDirectories.add(rel);
                        }
                    }
                } else if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (fileName.endsWith(".litematic") || fileName.endsWith(".schematic") ||
                        fileName.endsWith(".schem") || fileName.endsWith(".litematica") || fileName.endsWith(".nbt")) {
                        try {
                            String relativePath = schematicsDir.relativize(path).toString().replace('\\', '/');
                            String name = path.getFileName().toString();
                            if (name.lastIndexOf('.') > 0) {
                                name = name.substring(0, name.lastIndexOf('.'));
                            }
                            long size = Files.size(path);
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            String hash = computeHash(path);

                            SchematicMetadataDetails meta = extractMetadata(path, modified);

                            ServerSchematicInfo info = new ServerSchematicInfo(
                                    relativePath, name, size, hash, modified,
                                    meta.author, meta.timeCreated, meta.regionCount,
                                    meta.totalVolume, meta.totalBlocks,
                                    meta.sizeX, meta.sizeY, meta.sizeZ, meta.minecraftDataVersion
                            );

                            schematics.put(relativePath, info);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (IOException ignored) {
        }
    }

    public static class SchematicMetadataDetails {
        public final String author;
        public final long timeCreated;
        public final int regionCount;
        public final int totalVolume;
        public final int totalBlocks;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        public final int minecraftDataVersion;

        public SchematicMetadataDetails(String author, long timeCreated, int regionCount,
                                 int totalVolume, int totalBlocks,
                                 int sizeX, int sizeY, int sizeZ, int minecraftDataVersion) {
            this.author = author;
            this.timeCreated = timeCreated;
            this.regionCount = regionCount;
            this.totalVolume = totalVolume;
            this.totalBlocks = totalBlocks;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.minecraftDataVersion = minecraftDataVersion;
        }
    }

    public static SchematicMetadataDetails extractMetadata(Path path, long modified) {
        String author = "";
        long timeCreated = modified;
        int regionCount = 1;
        int totalVolume = 0;
        int totalBlocks = 0;
        int sizeX = 0, sizeY = 0, sizeZ = 0;
        int dataVersion = 0;

        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".litematic")) {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (root != null) {
                    dataVersion = root.getIntOr("MinecraftDataVersion", 0);
                    CompoundTag meta = root.getCompoundOrEmpty("Metadata");
                    author = meta.getStringOr("Author", "");
                    timeCreated = meta.getLongOr("TimeCreated", modified);
                    regionCount = meta.getIntOr("RegionCount", 1);
                    totalVolume = meta.getIntOr("TotalVolume", 0);
                    totalBlocks = meta.getIntOr("TotalBlocks", 0);
                    CompoundTag enc = meta.getCompoundOrEmpty("EnclosingSize");
                    sizeX = enc.getIntOr("x", 0);
                    sizeY = enc.getIntOr("y", 0);
                    sizeZ = enc.getIntOr("z", 0);
                }
            } else if (lower.endsWith(".schem")) {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (root != null) {
                    dataVersion = root.getIntOr("DataVersion", 0);
                    sizeX = root.getIntOr("Width", 0);
                    sizeY = root.getIntOr("Height", 0);
                    sizeZ = root.getIntOr("Length", 0);
                    totalVolume = sizeX * sizeY * sizeZ;
                    CompoundTag meta = root.getCompoundOrEmpty("Metadata");
                    author = meta.getStringOr("Author", "");
                    timeCreated = meta.getLongOr("Date", modified);
                }
            } else if (lower.endsWith(".nbt")) {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (root != null) {
                    dataVersion = root.getIntOr("DataVersion", 0);
                    ListTag sizeList = root.getListOrEmpty("size");
                    if (sizeList.size() >= 3) {
                        sizeX = sizeList.getIntOr(0, 0);
                        sizeY = sizeList.getIntOr(1, 0);
                        sizeZ = sizeList.getIntOr(2, 0);
                        totalVolume = sizeX * sizeY * sizeZ;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return new SchematicMetadataDetails(author, timeCreated, regionCount, totalVolume, totalBlocks, sizeX, sizeY, sizeZ, dataVersion);
    }

    public List<ServerSchematicInfo> getAllSchematics() {
        return new ArrayList<>(schematics.values());
    }

    public List<String> getAllDirectories() {
        return new ArrayList<>(serverDirectories);
    }

    public ServerSchematicInfo getSchematic(String id) {
        return schematics.get(id);
    }

    public void sendCatalogToPlayer(ServerPlayer player) {
        List<ServerSchematicInfo> allSchematics = getAllSchematics();
        SchematicListPayload payload = new SchematicListPayload(
                allSchematics,
                ServerPlacementManager.getInstance().getAllPlacements(),
                getSchematicsDir().toString(),
                getAllDirectories()
        );
        ServerPlayNetworking.send(player, payload);
    }

    public void broadcastCatalog(MinecraftServer server) {
        if (server == null) return;
        SchematicListPayload payload = new SchematicListPayload(
                getAllSchematics(),
                ServerPlacementManager.getInstance().getAllPlacements(),
                getSchematicsDir().toString(),
                getAllDirectories()
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public void handleDownloadRequest(ServerPlayer player, String schematicId) {
        ServerSchematicInfo info = schematics.get(schematicId);
        if (info == null) {
            return;
        }

        Path dir = getSchematicsDir();
        Path filePath;
        
        // Anti-Path Traversal: Check if normalized path stays inside schematics directory
        try {
            filePath = dir.resolve(schematicId).toAbsolutePath().normalize();
            if (!filePath.startsWith(dir.toAbsolutePath().normalize()) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                return;
            }
        } catch (InvalidPathException e) {
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            int totalChunks = (int) Math.ceil((double) fileBytes.length / CHUNK_SIZE);
            if (totalChunks == 0) {
                totalChunks = 1;
            }

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, fileBytes.length);
                byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);

                SchematicChunkPayload chunkPayload = new SchematicChunkPayload(
                        schematicId,
                        i,
                        totalChunks,
                        chunk
                );
                ServerPlayNetworking.send(player, chunkPayload);
            }
        } catch (IOException ignored) {
        }
    }

    public void handleUploadChunk(ServerPlayer player, UploadSchematicChunkPayload payload, MinecraftServer server) {
        String id = payload.schematicId().replace('\\', '/').trim();
        while (id.startsWith("/")) {
            id = id.substring(1);
        }

        if (id.contains("..") || id.contains(":") || id.isEmpty()) {
            return;
        }

        int chunkIdx = payload.chunkIndex();
        int total = payload.totalChunks();
        byte[] data = payload.data();

        long maxBytes = (long) ServerConfig.getInstance().getMaxUploadFileSizeMB() * 1024L * 1024L;
        if ((long) total * CHUNK_SIZE > maxBytes + CHUNK_SIZE) {
            return;
        }

        Map<Integer, byte[]> chunks = uploadChunks.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        chunks.put(chunkIdx, data);

        if (chunks.size() == total) {
            uploadChunks.remove(id);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int i = 0; i < total; i++) {
                    byte[] part = chunks.get(i);
                    if (part != null) {
                        baos.write(part);
                    }
                }
                Path dir = getSchematicsDir();
                Path target = dir.resolve(id).toAbsolutePath().normalize();
                if (!target.startsWith(dir.toAbsolutePath().normalize())) {
                    return;
                }
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, baos.toByteArray());

                scanSchematics();
                broadcastCatalog(server);
            } catch (IOException ignored) {
            }
        }
    }

    public void handleCreateDirectory(ServerPlayer player, String relPath, MinecraftServer server) {
        String clean = relPath.replace('\\', '/').trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.contains("..") || clean.contains(":") || clean.isEmpty()) {
            return;
        }
        try {
            Path dir = getSchematicsDir();
            Path target = dir.resolve(clean).toAbsolutePath().normalize();
            if (target.startsWith(dir.toAbsolutePath().normalize())) {
                Files.createDirectories(target);
                scanSchematics();
                broadcastCatalog(server);
            }
        } catch (IOException ignored) {
        }
    }

    private static String computeHash(Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
