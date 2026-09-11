package com.schematicsynchronizer.server;

import com.schematicsynchronizer.network.DownloadSchematicRequestPayload;
import com.schematicsynchronizer.network.PublishPlacementPayload;
import com.schematicsynchronizer.network.RemovePlacementPayload;
import com.schematicsynchronizer.network.RequestSchematicsPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class ModServerHandler {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerSchematicManager.getInstance().init(server);
            ServerPlacementManager.getInstance().load();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerSchematicManager.getInstance().scanSchematics();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerPlacementManager.getInstance().save();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerSchematicManager.getInstance().sendCatalogToPlayer(handler.getPlayer());
        });

        registerNetworkHandlers();
    }

    private static void registerNetworkHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestSchematicsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerSchematicManager.getInstance().scanSchematics();
                ServerSchematicManager.getInstance().sendCatalogToPlayer(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DownloadSchematicRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerSchematicManager.getInstance().handleDownloadRequest(player, payload.schematicId());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PublishPlacementPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                // Ensure the payload claims to belong to the sender (security)
                if (payload.placement() != null && payload.placement().getOwnerUuid().equals(player.getUUID())) {
                    ServerPlacementManager.getInstance().addOrUpdatePlacement(context.server(), payload.placement());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemovePlacementPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerPlacementManager.getInstance().removePlacement(context.server(), player.getUUID(), payload.placementId(), payload.schematicId());
            });
        });
    }
}
