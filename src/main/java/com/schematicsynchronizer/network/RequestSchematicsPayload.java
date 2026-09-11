package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestSchematicsPayload(String dummy) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestSchematicsPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("request_schematics"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSchematicsPayload> CODEC =
            CustomPacketPayload.codec(RequestSchematicsPayload::write, RequestSchematicsPayload::new);

    public RequestSchematicsPayload() {
        this("");
    }

    public RequestSchematicsPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dummy);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
