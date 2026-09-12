package com.schematicsynchronizer.client;

import com.schematicsynchronizer.network.SchematicChunkPayload;
import com.schematicsynchronizer.network.SchematicListPayload;
import com.schematicsynchronizer.network.SyncPlacementsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class SchematicSynchronizerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSchematicManager.getInstance().init();

        ClientPlayNetworking.registerGlobalReceiver(SchematicListPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientSchematicManager.getInstance().updateCatalog(payload.schematics(), payload.placements(), payload.serverDirectory(), payload.directories());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SchematicChunkPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientSchematicManager.getInstance().handleChunk(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPlacementsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientSchematicManager.getInstance().updatePlacements(payload.placements());
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientSchematicManager.getInstance().requestRefresh();
        });
    }
}
