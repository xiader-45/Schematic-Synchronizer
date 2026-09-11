package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record SyncPlacementsPayload(List<PlayerPlacementInfo> placements) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPlacementsPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("sync_placements"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlacementsPayload> CODEC =
            CustomPacketPayload.codec(SyncPlacementsPayload::write, SyncPlacementsPayload::new);

    public SyncPlacementsPayload(RegistryFriendlyByteBuf buf) {
        this(readPlacements(buf));
    }

    private static List<PlayerPlacementInfo> readPlacements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PlayerPlacementInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(PlayerPlacementInfo.read(buf));
        }
        return list;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(placements.size());
        for (PlayerPlacementInfo p : placements) {
            p.write(buf);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
