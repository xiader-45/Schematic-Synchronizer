package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record KickMemberHologramGroupPayload(String groupId, UUID memberUuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KickMemberHologramGroupPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("kick_member_hologram_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KickMemberHologramGroupPayload> CODEC =
            CustomPacketPayload.codec(KickMemberHologramGroupPayload::write, KickMemberHologramGroupPayload::new);

    public KickMemberHologramGroupPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUUID());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId != null ? groupId : "");
        buf.writeUUID(memberUuid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
