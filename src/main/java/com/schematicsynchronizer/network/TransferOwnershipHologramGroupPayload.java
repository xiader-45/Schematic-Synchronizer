package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record TransferOwnershipHologramGroupPayload(String groupId, UUID targetUuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransferOwnershipHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("transfer_ownership_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferOwnershipHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(TransferOwnershipHologramGroupPayload::write, TransferOwnershipHologramGroupPayload::new);

    public TransferOwnershipHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUUID());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeUUID(targetUuid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
