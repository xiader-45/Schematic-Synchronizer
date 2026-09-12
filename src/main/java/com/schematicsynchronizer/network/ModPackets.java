package com.schematicsynchronizer.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ModPackets {
    public static void registerCommon() {
        // Serverbound (C2S)
        PayloadTypeRegistry.serverboundPlay().register(RequestSchematicsPayload.TYPE, RequestSchematicsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DownloadSchematicRequestPayload.TYPE, DownloadSchematicRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PublishPlacementPayload.TYPE, PublishPlacementPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemovePlacementPayload.TYPE, RemovePlacementPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadSchematicChunkPayload.TYPE, UploadSchematicChunkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CreateServerDirectoryPayload.TYPE, CreateServerDirectoryPayload.CODEC);

        // Clientbound (S2C)
        PayloadTypeRegistry.clientboundPlay().register(SchematicListPayload.TYPE, SchematicListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SchematicChunkPayload.TYPE, SchematicChunkPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPlacementsPayload.TYPE, SyncPlacementsPayload.CODEC);
    }
}
