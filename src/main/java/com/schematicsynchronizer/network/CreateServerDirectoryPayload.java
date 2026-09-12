package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CreateServerDirectoryPayload(
        String directoryPath
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreateServerDirectoryPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("create_server_directory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateServerDirectoryPayload> CODEC =
            CustomPacketPayload.codec(CreateServerDirectoryPayload::write, CreateServerDirectoryPayload::new);

    public CreateServerDirectoryPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(directoryPath);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
