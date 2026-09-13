package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LeaveHologramGroupPayload(String groupId, int action) implements CustomPacketPayload {
    public static final int ACTION_DELETE = 0;
    public static final int ACTION_TRANSFER_RANDOM = 1;
    public static final int ACTION_LEAVE = 2;

    public static final CustomPacketPayload.Type<LeaveHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("leave_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(LeaveHologramGroupPayload::write, LeaveHologramGroupPayload::new);

    public LeaveHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeVarInt(action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
