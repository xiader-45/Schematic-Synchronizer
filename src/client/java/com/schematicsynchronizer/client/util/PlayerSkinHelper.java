package com.schematicsynchronizer.client.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PlayerSkinHelper {
    private static final Map<UUID, PlayerSkin> CACHE_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<String, PlayerSkin> CACHE_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, Supplier<PlayerSkin>> SKIN_SUPPLIERS = new ConcurrentHashMap<>();

    public static PlayerSkin getSkinForPlayer(UUID uuid, String name) {
        // 1. Check in-memory permanent session cache first
        if (uuid != null) {
            PlayerSkin cached = CACHE_BY_UUID.get(uuid);
            if (cached != null) return cached;
        }
        if (name != null && !name.isEmpty()) {
            PlayerSkin cached = CACHE_BY_NAME.get(name.toLowerCase());
            if (cached != null) return cached;
        }

        Minecraft mc = Minecraft.getInstance();

        // 2. Check local client player
        if (mc.player != null) {
            UUID localUuid = mc.player.getUUID();
            String localName = mc.player.getName().getString();
            if ((uuid != null && uuid.equals(localUuid)) || (name != null && name.equalsIgnoreCase(localName))) {
                PlayerSkin skin = mc.player.getSkin();
                if (skin != null) {
                    cachePlayerSkin(localUuid, localName, skin);
                    return skin;
                }
            }
        }

        // 3. Check current tab list / connection
        if (mc.getConnection() != null) {
            PlayerInfo info = (uuid != null) ? mc.getConnection().getPlayerInfo(uuid) : null;
            if (info == null && name != null) {
                info = mc.getConnection().getPlayerInfo(name);
            }
            if (info != null) {
                PlayerSkin skin = info.getSkin();
                if (skin != null) {
                    cachePlayerSkin(uuid, name, skin);
                    return skin;
                }
            }

            // Proactively check any online player matching name
            if (name != null) {
                for (PlayerInfo pInfo : mc.getConnection().getOnlinePlayers()) {
                    if (pInfo.getProfile().name().equalsIgnoreCase(name)) {
                        PlayerSkin skin = pInfo.getSkin();
                        if (skin != null) {
                            cachePlayerSkin(pInfo.getProfile().id(), name, skin);
                            return skin;
                        }
                    }
                }
            }
        }

        // 4. Async lookup via SkinManager
        if (uuid != null) {
            Supplier<PlayerSkin> supplier = SKIN_SUPPLIERS.computeIfAbsent(uuid, u -> {
                GameProfile gp = new GameProfile(u, name != null ? name : "");
                return mc.getSkinManager().createLookup(gp, true);
            });
            PlayerSkin skin = supplier.get();
            if (skin != null) {
                cachePlayerSkin(uuid, name, skin);
                return skin;
            }
        }

        // Fallback default skin
        return (uuid != null) ? DefaultPlayerSkin.get(uuid) : DefaultPlayerSkin.getDefaultSkin();
    }

    public static void cachePlayerSkin(UUID uuid, String name, PlayerSkin skin) {
        if (skin == null) return;
        if (uuid != null) {
            CACHE_BY_UUID.put(uuid, skin);
        }
        if (name != null && !name.isEmpty()) {
            CACHE_BY_NAME.put(name.toLowerCase(), skin);
        }
    }
}
