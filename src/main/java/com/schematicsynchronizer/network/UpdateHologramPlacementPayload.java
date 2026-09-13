package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateHologramPlacementPayload(String groupId, String placementId, BlockPos origin,
                                             String rotation, String mirror, boolean locked, boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateHologramPlacementPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("update_hologram_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateHologramPlacementPayload> CODEC =
            CustomPacketPayload.codec(UpdateHologramPlacementPayload::write, UpdateHologramPlacementPayload::new);

    public UpdateHologramPlacementPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeUtf(placementId != null ? placementId : "");
        buf.writeBlockPos(origin != null ? origin : BlockPos.ZERO);
        buf.writeUtf(rotation != null ? rotation : "NONE");
        buf.writeUtf(mirror != null ? mirror : "NONE");
        buf.writeBoolean(locked);
        buf.writeBoolean(enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
