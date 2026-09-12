package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.GroupPlacementData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record CreateHologramGroupPayload(String name, String dimension, List<GroupPlacementData> initialPlacements) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreateHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("create_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(CreateHologramGroupPayload::write, CreateHologramGroupPayload::new);

    public CreateHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), readPlacements(buf));
    }

    public CreateHologramGroupPayload(String name, String dimension) {
        this(name, dimension, new ArrayList<>());
    }

    private static List<GroupPlacementData> readPlacements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<GroupPlacementData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(GroupPlacementData.read(buf));
        }
        return list;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(name != null ? name : "");
        buf.writeUtf(dimension != null ? dimension : "");
        buf.writeVarInt(initialPlacements != null ? initialPlacements.size() : 0);
        if (initialPlacements != null) {
            for (GroupPlacementData p : initialPlacements) {
                p.write(buf);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
