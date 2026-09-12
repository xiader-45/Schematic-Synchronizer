package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestHologramGroupsPayload(String dimension) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestHologramGroupsPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("request_hologram_groups"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHologramGroupsPayload> CODEC =
            CustomPacketPayload.codec(RequestHologramGroupsPayload::write, RequestHologramGroupsPayload::new);

    public RequestHologramGroupsPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dimension != null ? dimension : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
