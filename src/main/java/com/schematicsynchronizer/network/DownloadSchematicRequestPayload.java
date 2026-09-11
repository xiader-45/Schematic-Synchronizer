package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DownloadSchematicRequestPayload(String schematicId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DownloadSchematicRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("download_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DownloadSchematicRequestPayload> CODEC =
            CustomPacketPayload.codec(DownloadSchematicRequestPayload::write, DownloadSchematicRequestPayload::new);

    public DownloadSchematicRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(schematicId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
