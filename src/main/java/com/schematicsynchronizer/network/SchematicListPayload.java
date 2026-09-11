package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record SchematicListPayload(
        List<ServerSchematicInfo> schematics,
        List<PlayerPlacementInfo> placements,
        String serverDirectory
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SchematicListPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("schematic_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SchematicListPayload> CODEC =
            CustomPacketPayload.codec(SchematicListPayload::write, SchematicListPayload::new);

    public SchematicListPayload(RegistryFriendlyByteBuf buf) {
        this(readSchematics(buf), readPlacements(buf), buf.readUtf());
    }

    private static List<ServerSchematicInfo> readSchematics(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ServerSchematicInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(ServerSchematicInfo.read(buf));
        }
        return list;
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
        buf.writeVarInt(schematics.size());
        for (ServerSchematicInfo s : schematics) {
            s.write(buf);
        }
        buf.writeVarInt(placements.size());
        for (PlayerPlacementInfo p : placements) {
            p.write(buf);
        }
        buf.writeUtf(serverDirectory != null ? serverDirectory : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
