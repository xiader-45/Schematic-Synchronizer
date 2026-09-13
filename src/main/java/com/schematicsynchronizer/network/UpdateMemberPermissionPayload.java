package com.schematicsynchronizer.network;

import com.schematicsynchronizer.SchematicSynchronizer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record UpdateMemberPermissionPayload(String groupId, UUID memberUuid, String permission) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateMemberPermissionPayload> TYPE =
            new CustomPacketPayload.Type<>(SchematicSynchronizer.id("update_member_permission"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMemberPermissionPayload> CODEC =
            CustomPacketPayload.codec(UpdateMemberPermissionPayload::write, UpdateMemberPermissionPayload::new);

    public UpdateMemberPermissionPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUUID(), buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(groupId);
        buf.writeUUID(memberUuid);
        buf.writeUtf(permission);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
