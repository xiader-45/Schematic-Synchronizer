package com.schematicsynchronizer.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerConfig {
    private static final ServerConfig INSTANCE = new ServerConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String schematicsDirectory = "schematics";
    private boolean allowUploads = true;
    private boolean allowCreateDirectories = true;
    private int maxUploadFileSizeMB = 50;
    private boolean syncPlacements = true;

    public static ServerConfig getInstance() {
        return INSTANCE;
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("schematic-synchronizer").resolve("config.json");
    }

    public synchronized void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("schematicsDirectory")) {
                this.schematicsDirectory = obj.get("schematicsDirectory").getAsString();
            }
            if (obj.has("allowUploads")) {
                this.allowUploads = obj.get("allowUploads").getAsBoolean();
            }
            if (obj.has("allowCreateDirectories")) {
                this.allowCreateDirectories = obj.get("allowCreateDirectories").getAsBoolean();
            }
            if (obj.has("maxUploadFileSizeMB")) {
                this.maxUploadFileSizeMB = obj.get("maxUploadFileSizeMB").getAsInt();
            }
            if (obj.has("syncPlacements")) {
                this.syncPlacements = obj.get("syncPlacements").getAsBoolean();
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("schematicsDirectory", this.schematicsDirectory);
            obj.addProperty("allowUploads", this.allowUploads);
            obj.addProperty("allowCreateDirectories", this.allowCreateDirectories);
            obj.addProperty("maxUploadFileSizeMB", this.maxUploadFileSizeMB);
            obj.addProperty("syncPlacements", this.syncPlacements);

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public String getSchematicsDirectory() {
        return schematicsDirectory;
    }

    public void setSchematicsDirectory(String schematicsDirectory) {
        this.schematicsDirectory = schematicsDirectory;
    }

    public boolean isAllowUploads() {
        return allowUploads;
    }

    public void setAllowUploads(boolean allowUploads) {
        this.allowUploads = allowUploads;
    }

    public boolean isAllowCreateDirectories() {
        return allowCreateDirectories;
    }

    public void setAllowCreateDirectories(boolean allowCreateDirectories) {
        this.allowCreateDirectories = allowCreateDirectories;
    }

    public int getMaxUploadFileSizeMB() {
        return maxUploadFileSizeMB;
    }

    public void setMaxUploadFileSizeMB(int maxUploadFileSizeMB) {
        this.maxUploadFileSizeMB = maxUploadFileSizeMB;
    }

    public boolean isSyncPlacements() {
        return syncPlacements;
    }

    public void setSyncPlacements(boolean syncPlacements) {
        this.syncPlacements = syncPlacements;
    }
}
