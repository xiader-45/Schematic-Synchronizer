package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.GroupPlacementData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record AddGroupPlacementsPayload(String groupId, List<GroupPlacementData> placements) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AddGroupPlacementsPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("add_group_placements"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddGroupPlacementsPayload> CODEC =
            CustomPacketPayload.codec(AddGroupPlacementsPayload::write, AddGroupPlacementsPayload::new);

    public AddGroupPlacementsPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), readPlacements(buf));
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
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeVarInt(placements != null ? placements.size() : 0);
        if (placements != null) {
            for (GroupPlacementData p : placements) {
                p.write(buf);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
