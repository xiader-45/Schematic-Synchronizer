package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UploadSchematicChunkPayload(
        String schematicId,
        int chunkIndex,
        int totalChunks,
        byte[] data
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UploadSchematicChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("upload_schematic_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UploadSchematicChunkPayload> CODEC =
            CustomPacketPayload.codec(UploadSchematicChunkPayload::write, UploadSchematicChunkPayload::new);

    public UploadSchematicChunkPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(schematicId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
