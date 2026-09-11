package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PublishPlacementPayload(PlayerPlacementInfo placement) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PublishPlacementPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("publish_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PublishPlacementPayload> CODEC =
            CustomPacketPayload.codec(PublishPlacementPayload::write, PublishPlacementPayload::new);

    public PublishPlacementPayload(RegistryFriendlyByteBuf buf) {
        this(PlayerPlacementInfo.read(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        placement.write(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
