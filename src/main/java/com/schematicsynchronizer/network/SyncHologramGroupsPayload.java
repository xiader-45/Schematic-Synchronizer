package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.HologramGroupData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record SyncHologramGroupsPayload(String dimension, List<HologramGroupData> groups) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncHologramGroupsPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("sync_hologram_groups"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHologramGroupsPayload> CODEC =
            CustomPacketPayload.codec(SyncHologramGroupsPayload::write, SyncHologramGroupsPayload::new);

    public SyncHologramGroupsPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), readGroups(buf));
    }

    private static List<HologramGroupData> readGroups(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<HologramGroupData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(HologramGroupData.read(buf));
        }
        return list;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(dimension != null ? dimension : "");
        buf.writeVarInt(groups != null ? groups.size() : 0);
        if (groups != null) {
            for (HologramGroupData g : groups) {
                g.write(buf);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
