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

        PayloadTypeRegistry.serverboundPlay().register(RequestHologramGroupsPayload.TYPE, RequestHologramGroupsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CreateHologramGroupPayload.TYPE, CreateHologramGroupPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AddGroupPlacementsPayload.TYPE, AddGroupPlacementsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveGroupPlacementPayload.TYPE, RemoveGroupPlacementPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JoinHologramGroupPayload.TYPE, JoinHologramGroupPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LeaveHologramGroupPayload.TYPE, LeaveHologramGroupPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KickMemberHologramGroupPayload.TYPE, KickMemberHologramGroupPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TransferOwnershipHologramGroupPayload.TYPE, TransferOwnershipHologramGroupPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateHologramPlacementPayload.TYPE, UpdateHologramPlacementPayload.CODEC);

        // Clientbound (S2C)
        PayloadTypeRegistry.clientboundPlay().register(SchematicListPayload.TYPE, SchematicListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SchematicChunkPayload.TYPE, SchematicChunkPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPlacementsPayload.TYPE, SyncPlacementsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncHologramGroupsPayload.TYPE, SyncHologramGroupsPayload.CODEC);
    }
}
