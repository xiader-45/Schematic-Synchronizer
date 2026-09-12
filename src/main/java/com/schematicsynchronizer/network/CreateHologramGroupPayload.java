package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CreateHologramGroupPayload(String name, String schematicId, String dimension,
                                        BlockPos origin, String rotation, String mirror) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreateHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("create_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(CreateHologramGroupPayload::write, CreateHologramGroupPayload::new);

    public CreateHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readUtf(), buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(name != null ? name : "");
        buf.writeUtf(schematicId != null ? schematicId : "");
        buf.writeUtf(dimension != null ? dimension : "");
        buf.writeBlockPos(origin != null ? origin : BlockPos.ZERO);
        buf.writeUtf(rotation != null ? rotation : "NONE");
        buf.writeUtf(mirror != null ? mirror : "NONE");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
