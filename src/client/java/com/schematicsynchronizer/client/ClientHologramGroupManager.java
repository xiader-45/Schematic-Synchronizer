package com.schematicsynchronizer.client;

import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.*;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHologramGroupManager {
    private static final ClientHologramGroupManager INSTANCE = new ClientHologramGroupManager();
    private static final IStringConsumer DUMMY_STR_CONSUMER = s -> {};
    private static final IMessageConsumer DUMMY_MSG_CONSUMER = new IMessageConsumer() {
        @Override
        public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType type, String msg, Object... args) {}

        @Override
        public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType type, int time, String msg, Object... args) {}
    };

    private String currentDimension = "minecraft:overworld";
    private final List<HologramGroupData> groups = new ArrayList<>();
    private final Map<UUID, String> placementToGroupId = new ConcurrentHashMap<>();
    private final Map<String, UUID> groupToPlacementId = new ConcurrentHashMap<>();

    private Runnable guiRefreshCallback = null;

    public static ClientHologramGroupManager getInstance() {
        return INSTANCE;
    }

    public void setGuiRefreshCallback(Runnable callback) {
        this.guiRefreshCallback = callback;
    }

    public List<HologramGroupData> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public String getCurrentDimension() {
        return currentDimension;
    }

    public HologramGroupData getGroupById(String groupId) {
        if (groupId == null) return null;
        for (HologramGroupData g : groups) {
            if (g.getId().equals(groupId)) return g;
        }
        return null;
    }

    public HologramGroupData getGroupForPlacement(SchematicPlacement placement) {
        if (placement == null) return null;
        UUID id = placement.getHashId();
        if (id == null) return null;
        String groupId = placementToGroupId.get(id);
        return groupId != null ? getGroupById(groupId) : null;
    }

    public boolean isPlacementInGroup(SchematicPlacement placement) {
        return getGroupForPlacement(placement) != null;
    }

    public boolean isPlacementOwner(SchematicPlacement placement) {
        HologramGroupData group = getGroupForPlacement(placement);
        if (group == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return group.isOwner(mc.getUser().getProfileId());
    }

    public void requestGroups() {
        Minecraft mc = Minecraft.getInstance();
        String dim = (mc.level != null) ? mc.level.dimension().identifier().toString() : "minecraft:overworld";
        this.currentDimension = dim;
        if (ClientPlayNetworking.canSend(RequestHologramGroupsPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestHologramGroupsPayload(dim));
        }
    }

    public void createGroup(String name, String schematicId, BlockPos origin, Rotation rotation, Mirror mirror) {
        Minecraft mc = Minecraft.getInstance();
        String dim = (mc.level != null) ? mc.level.dimension().identifier().toString() : "minecraft:overworld";
        if (ClientPlayNetworking.canSend(CreateHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new CreateHologramGroupPayload(
                    name,
                    schematicId,
                    dim,
                    origin != null ? origin : BlockPos.ZERO,
                    rotation != null ? rotation.name() : "NONE",
                    mirror != null ? mirror.name() : "NONE"
            ));
        }
    }

    public void joinGroup(String groupId) {
        if (ClientPlayNetworking.canSend(JoinHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new JoinHologramGroupPayload(groupId));
        }
    }

    public void leaveGroup(String groupId, int action) {
        if (ClientPlayNetworking.canSend(LeaveHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new LeaveHologramGroupPayload(groupId, action));
        }
    }

    public void kickMember(String groupId, UUID memberUuid) {
        if (ClientPlayNetworking.canSend(KickMemberHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new KickMemberHologramGroupPayload(groupId, memberUuid));
        }
    }

    public void transferOwnership(String groupId, UUID targetUuid) {
        if (ClientPlayNetworking.canSend(TransferOwnershipHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new TransferOwnershipHologramGroupPayload(groupId, targetUuid));
        }
    }

    public void onPlacementModified(SchematicPlacement placement) {
        if (placement == null) return;
        HologramGroupData group = getGroupForPlacement(placement);
        if (group == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        if (group.isOwner(myUuid)) {
            // Owner is modifying: send update to server
            if (ClientPlayNetworking.canSend(UpdateHologramPlacementPayload.TYPE)) {
                ClientPlayNetworking.send(new UpdateHologramPlacementPayload(
                        group.getId(),
                        placement.getOrigin(),
                        placement.getRotation().name(),
                        placement.getMirror().name(),
                        placement.isLocked()
                ));
            }
        } else {
            // Non-owner attempted to modify: immediately revert!
            placement.setOrigin(group.getOrigin(), DUMMY_STR_CONSUMER);
            placement.setRotation(parseRotation(group.getRotation()), DUMMY_MSG_CONSUMER);
            placement.setMirror(parseMirror(group.getMirror()), DUMMY_MSG_CONSUMER);
            if (!placement.isLocked()) {
                placement.toggleLocked();
            }
        }
    }

    public void handleSyncGroups(SyncHologramGroupsPayload payload) {
        this.currentDimension = payload.dimension();
        this.groups.clear();
        if (payload.groups() != null) {
            this.groups.addAll(payload.groups());
        }

        syncWithLitematicaPlacements();

        if (this.guiRefreshCallback != null) {
            try {
                this.guiRefreshCallback.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void syncWithLitematicaPlacements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> allPlacements = placementManager.getAllSchematicsPlacements();

        Set<String> activeGroupIds = new HashSet<>();

        for (HologramGroupData group : this.groups) {
            activeGroupIds.add(group.getId());
            boolean isMember = group.isMember(myUuid);

            if (isMember) {
                // Find existing placement
                UUID existingPlacementId = groupToPlacementId.get(group.getId());
                SchematicPlacement matchedPlacement = null;

                if (existingPlacementId != null) {
                    for (SchematicPlacement p : allPlacements) {
                        if (p.getHashId().equals(existingPlacementId)) {
                            matchedPlacement = p;
                            break;
                        }
                    }
                }

                // If not matched by registered ID, check if any placement matches schematic and group name
                if (matchedPlacement == null) {
                    for (SchematicPlacement p : allPlacements) {
                        if (p.getName().equalsIgnoreCase(group.getName()) ||
                                (placementToGroupId.containsKey(p.getHashId()) && placementToGroupId.get(p.getHashId()).equals(group.getId()))) {
                            matchedPlacement = p;
                            groupToPlacementId.put(group.getId(), p.getHashId());
                            placementToGroupId.put(p.getHashId(), group.getId());
                            break;
                        }
                    }
                }

                if (matchedPlacement != null) {
                    // Update parameters
                    boolean isOwner = group.isOwner(myUuid);
                    if (!isOwner) {
                        // Non-owner receives parameters from owner
                        if (!matchedPlacement.getOrigin().equals(group.getOrigin())) {
                            matchedPlacement.setOrigin(group.getOrigin(), DUMMY_STR_CONSUMER);
                        }
                        Rotation rot = parseRotation(group.getRotation());
                        if (matchedPlacement.getRotation() != rot) {
                            matchedPlacement.setRotation(rot, DUMMY_MSG_CONSUMER);
                        }
                        Mirror mir = parseMirror(group.getMirror());
                        if (matchedPlacement.getMirror() != mir) {
                            matchedPlacement.setMirror(mir, DUMMY_MSG_CONSUMER);
                        }
                        // Non-owner is locked from editing
                        if (!matchedPlacement.isLocked()) {
                            matchedPlacement.toggleLocked();
                        }
                    } else {
                        if (matchedPlacement.isLocked() != group.isLocked()) {
                            matchedPlacement.toggleLocked();
                        }
                    }
                } else {
                    // Placement does not exist yet: create it!
                    loadAndCreatePlacementForGroup(group, !group.isOwner(myUuid));
                }
            } else {
                // Player is NOT a member: if there was a linked placement, remove it!
                UUID existingId = groupToPlacementId.remove(group.getId());
                if (existingId != null) {
                    placementToGroupId.remove(existingId);
                    for (SchematicPlacement p : allPlacements) {
                        if (p.getHashId().equals(existingId)) {
                            placementManager.removeSchematicPlacement(p);
                            break;
                        }
                    }
                }
            }
        }

        // Clean up groups that no longer exist
        Iterator<Map.Entry<String, UUID>> it = groupToPlacementId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, UUID> entry = it.next();
            if (!activeGroupIds.contains(entry.getKey())) {
                UUID pId = entry.getValue();
                placementToGroupId.remove(pId);
                it.remove();
                for (SchematicPlacement p : allPlacements) {
                    if (p.getHashId().equals(pId)) {
                        placementManager.removeSchematicPlacement(p);
                        break;
                    }
                }
            }
        }
    }

    public void registerPlacementForGroup(String groupId, SchematicPlacement placement) {
        if (groupId == null || placement == null) return;
        groupToPlacementId.put(groupId, placement.getHashId());
        placementToGroupId.put(placement.getHashId(), groupId);
    }

    private void loadAndCreatePlacementForGroup(HologramGroupData group, boolean lock) {
        Path localFile = ClientSchematicManager.getInstance().getLocalFilePath(group.getSchematicId());
        if (localFile != null && Files.exists(localFile)) {
            createPlacementFromPath(group, localFile, lock);
        } else {
            // Request download from server
            ClientSchematicManager.getInstance().downloadSchematic(group.getSchematicId(), path -> {
                Path downloaded = ClientSchematicManager.getInstance().getLocalFilePath(group.getSchematicId());
                if (downloaded != null && Files.exists(downloaded)) {
                    createPlacementFromPath(group, downloaded, lock);
                }
            });
        }
    }

    private void createPlacementFromPath(HologramGroupData group, Path path, boolean lock) {
        try {
            LitematicaSchematic schematic = ClientSchematicManager.getInstance().loadSchematicFromFile(path);
            if (schematic == null) return;

            UUID placementId = UUID.randomUUID();
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, group.getOrigin(), group.getName(), true, true, placementId);
            placement.setRotation(parseRotation(group.getRotation()), DUMMY_MSG_CONSUMER);
            placement.setMirror(parseMirror(group.getMirror()), DUMMY_MSG_CONSUMER);
            if (lock && !placement.isLocked()) {
                placement.toggleLocked();
            }

            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
            groupToPlacementId.put(group.getId(), placement.getHashId());
            placementToGroupId.put(placement.getHashId(), group.getId());
        } catch (Exception ignored) {
        }
    }

    public static Rotation parseRotation(String name) {
        if (name == null) return Rotation.NONE;
        try {
            return Rotation.valueOf(name);
        } catch (Exception e) {
            return Rotation.NONE;
        }
    }

    public static Mirror parseMirror(String name) {
        if (name == null) return Mirror.NONE;
        try {
            return Mirror.valueOf(name);
        } catch (Exception e) {
            return Mirror.NONE;
        }
    }
}
