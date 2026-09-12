package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemoveGroupPlacementPayload(String groupId, String placementId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoveGroupPlacementPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("remove_group_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveGroupPlacementPayload> CODEC =
            CustomPacketPayload.codec(RemoveGroupPlacementPayload::write, RemoveGroupPlacementPayload::new);

    public RemoveGroupPlacementPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeUtf(placementId != null ? placementId : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
