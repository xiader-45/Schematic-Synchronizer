package com.schematicsynchronizer.server;

import com.schematicsynchronizer.network.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class ModServerHandler {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerConfig.getInstance().load();
            ServerSchematicManager.getInstance().init(server);
            ServerPlacementManager.getInstance().load();
            ServerHologramGroupManager.getInstance().init(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerSchematicManager.getInstance().scanSchematics();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerPlacementManager.getInstance().save();
            ServerConfig.getInstance().save();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerSchematicManager.getInstance().sendCatalogToPlayer(handler.getPlayer());
            ServerHologramGroupManager.getInstance().sendGroupsToPlayer(handler.getPlayer(), null);
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
                if (!ServerConfig.getInstance().isSyncPlacements()) {
                    return;
                }
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

        ServerPlayNetworking.registerGlobalReceiver(UploadSchematicChunkPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!ServerConfig.getInstance().isAllowUploads()) {
                    return;
                }
                ServerSchematicManager.getInstance().handleUploadChunk(player, payload, context.server());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CreateServerDirectoryPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!ServerConfig.getInstance().isAllowCreateDirectories()) {
                    return;
                }
                ServerSchematicManager.getInstance().handleCreateDirectory(player, payload.directoryPath(), context.server());
            });
        });

        // Hologram Group Networking
        ServerPlayNetworking.registerGlobalReceiver(RequestHologramGroupsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().sendGroupsToPlayer(player, payload.dimension());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CreateHologramGroupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().createGroup(context.server(), player, payload);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AddGroupPlacementsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().addPlacements(context.server(), player, payload);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveGroupPlacementPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().removePlacement(context.server(), player, payload);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinHologramGroupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().joinGroup(context.server(), player, payload.groupId());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(LeaveHologramGroupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().leaveGroup(context.server(), player, payload.groupId(), payload.action());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(KickMemberHologramGroupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().kickMember(context.server(), player, payload.groupId(), payload.memberUuid());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TransferOwnershipHologramGroupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().transferOwnership(context.server(), player, payload.groupId(), payload.targetUuid());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateHologramPlacementPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ServerHologramGroupManager.getInstance().updatePlacement(context.server(), player, payload);
            });
        });
    }
}
