package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record JoinHologramGroupPayload(String groupId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JoinHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("join_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JoinHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(JoinHologramGroupPayload::write, JoinHologramGroupPayload::new);

    public JoinHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
