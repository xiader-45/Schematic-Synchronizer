package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record RemovePlacementPayload(UUID placementId, String schematicId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemovePlacementPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("remove_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemovePlacementPayload> CODEC =
            CustomPacketPayload.codec(RemovePlacementPayload::write, RemovePlacementPayload::new);

    public RemovePlacementPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean() ? buf.readUUID() : null, buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(placementId != null);
        if (placementId != null) {
            buf.writeUUID(placementId);
        }
        buf.writeUtf(schematicId != null ? schematicId : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
