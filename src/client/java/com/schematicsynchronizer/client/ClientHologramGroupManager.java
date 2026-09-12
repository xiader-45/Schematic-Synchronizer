package com.schematicsynchronizer.client;

import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.*;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.gui.Message;

public class ClientHologramGroupManager {
    private static final ClientHologramGroupManager INSTANCE = new ClientHologramGroupManager();

    private final List<HologramGroupData> groups = new ArrayList<>();
    private String currentDimension = "minecraft:overworld";
    private boolean canOpManage = false;
    private Runnable guiRefreshCallback;

    public record GroupPlacementKey(String groupId, String placementId) {}

    // Maps Litematica placement HashId <-> (groupId, placementId)
    private final Map<UUID, GroupPlacementKey> placementToGroupKey = new ConcurrentHashMap<>();
    private final Map<GroupPlacementKey, UUID> groupKeyToPlacementId = new ConcurrentHashMap<>();

    private static final IStringConsumer DUMMY_STR_CONSUMER = s -> {};
    private static final IMessageConsumer DUMMY_MSG_CONSUMER = new IMessageConsumer() {
        @Override
        public void addMessage(Message.MessageType type, String messageKey, Object... args) {}
        @Override
        public void addMessage(Message.MessageType type, int displayTime, String messageKey, Object... args) {}
    };

    private ClientHologramGroupManager() {}

    public static ClientHologramGroupManager getInstance() {
        return INSTANCE;
    }

    public List<HologramGroupData> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public boolean canOpManage() {
        return canOpManage;
    }

    public void setGuiRefreshCallback(Runnable callback) {
        this.guiRefreshCallback = callback;
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
        if (id != null) {
            GroupPlacementKey key = placementToGroupKey.get(id);
            if (key != null) {
                HologramGroupData g = getGroupById(key.groupId());
                if (g != null) return g;
            }
        }
        for (HologramGroupData group : this.groups) {
            for (GroupPlacementData pData : group.getPlacements()) {
                if (pData.getName().equalsIgnoreCase(placement.getName()) &&
                        pData.getOrigin().equals(placement.getOrigin())) {
                    if (id != null) {
                        GroupPlacementKey newKey = new GroupPlacementKey(group.getId(), pData.getId());
                        placementToGroupKey.put(id, newKey);
                        groupKeyToPlacementId.put(newKey, id);
                    }
                    return group;
                }
            }
        }
        return null;
    }

    public void registerPlacementForGroup(String groupId, String placementId, UUID litematicaPlacementId) {
        if (groupId == null || placementId == null || litematicaPlacementId == null) return;
        GroupPlacementKey key = new GroupPlacementKey(groupId, placementId);
        groupKeyToPlacementId.put(key, litematicaPlacementId);
        placementToGroupKey.put(litematicaPlacementId, key);
    }

    public GroupPlacementKey getPlacementKey(SchematicPlacement placement) {
        if (placement == null) return null;
        UUID id = placement.getHashId();
        return id != null ? placementToGroupKey.get(id) : null;
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

    public void createGroup(String name, List<GroupPlacementData> initialPlacements) {
        Minecraft mc = Minecraft.getInstance();
        String dim = (mc.level != null) ? mc.level.dimension().identifier().toString() : "minecraft:overworld";
        if (ClientPlayNetworking.canSend(CreateHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new CreateHologramGroupPayload(
                    name,
                    dim,
                    initialPlacements != null ? initialPlacements : Collections.emptyList()
            ));
        }
    }

    public void addPlacementsToGroup(String groupId, List<GroupPlacementData> placements) {
        if (groupId == null || placements == null || placements.isEmpty()) return;
        HologramGroupData localGroup = getGroupById(groupId);
        if (localGroup != null) {
            for (GroupPlacementData p : placements) {
                localGroup.addPlacement(p);
            }
            if (this.guiRefreshCallback != null) {
                try {
                    this.guiRefreshCallback.run();
                } catch (Exception ignored) {}
            }
        }
        if (ClientPlayNetworking.canSend(AddGroupPlacementsPayload.TYPE)) {
            ClientPlayNetworking.send(new AddGroupPlacementsPayload(groupId, placements));
        }
    }

    public void removePlacementFromGroup(String groupId, String placementId) {
        if (groupId == null || placementId == null) return;
        HologramGroupData localGroup = getGroupById(groupId);
        if (localGroup != null) {
            localGroup.removePlacement(placementId);
            GroupPlacementKey key = new GroupPlacementKey(groupId, placementId);
            UUID pId = groupKeyToPlacementId.remove(key);
            if (pId != null) {
                placementToGroupKey.remove(pId);
            }
            if (this.guiRefreshCallback != null) {
                try {
                    this.guiRefreshCallback.run();
                } catch (Exception ignored) {}
            }
        }
        if (ClientPlayNetworking.canSend(RemoveGroupPlacementPayload.TYPE)) {
            ClientPlayNetworking.send(new RemoveGroupPlacementPayload(groupId, placementId));
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
        GroupPlacementKey key = placementToGroupKey.get(placement.getHashId());
        if (key == null) return;

        HologramGroupData group = getGroupById(key.groupId());
        if (group == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        GroupPlacementData pData = group.getPlacement(key.placementId());

        if (group.isOwner(myUuid) || canOpManage) {
            // Owner / OP is modifying: send update to server
            if (ClientPlayNetworking.canSend(UpdateHologramPlacementPayload.TYPE)) {
                ClientPlayNetworking.send(new UpdateHologramPlacementPayload(
                        group.getId(),
                        key.placementId(),
                        placement.getOrigin(),
                        placement.getRotation().name(),
                        placement.getMirror().name(),
                        placement.isLocked()
                ));
            }
        } else {
            // Non-owner attempted to modify: immediately revert to group values!
            if (pData != null) {
                placement.setOrigin(pData.getOrigin(), DUMMY_STR_CONSUMER);
                placement.setRotation(parseRotation(pData.getRotation()), DUMMY_MSG_CONSUMER);
                placement.setMirror(parseMirror(pData.getMirror()), DUMMY_MSG_CONSUMER);
            }
            if (!placement.isLocked()) {
                placement.toggleLocked();
            }
        }
    }

    public void handleSyncGroups(SyncHologramGroupsPayload payload) {
        this.currentDimension = payload.dimension();
        this.canOpManage = payload.canOpManage();
        this.groups.clear();
        if (payload.groups() != null) {
            this.groups.addAll(payload.groups());
        }

        checkAndDownloadMissingGroupSchematics();
        syncWithLitematicaPlacements();

        if (this.guiRefreshCallback != null) {
            try {
                this.guiRefreshCallback.run();
            } catch (Exception ignored) {
            }
        }
    }

    public void checkAndDownloadMissingGroupSchematics() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        for (HologramGroupData group : this.groups) {
            if (group.isMember(myUuid)) {
                for (GroupPlacementData p : group.getPlacements()) {
                    String schemId = p.getSchematicId();
                    if (schemId != null && !schemId.isEmpty() && !ClientSchematicManager.getInstance().isSchematicAvailableLocally(schemId)) {
                        ClientSchematicManager.getInstance().downloadSchematic(schemId, path -> {
                            syncWithLitematicaPlacements();
                        });
                    }
                }
            }
        }
    }

    private void syncWithLitematicaPlacements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> allPlacements = placementManager.getAllSchematicsPlacements();

        Set<GroupPlacementKey> activeKeys = new HashSet<>();

        for (HologramGroupData group : this.groups) {
            boolean isMember = group.isMember(myUuid);

            if (isMember) {
                for (GroupPlacementData pData : group.getPlacements()) {
                    GroupPlacementKey key = new GroupPlacementKey(group.getId(), pData.getId());
                    activeKeys.add(key);

                    UUID existingPlacementId = groupKeyToPlacementId.get(key);
                    SchematicPlacement matchedPlacement = null;

                    if (existingPlacementId != null) {
                        for (SchematicPlacement p : allPlacements) {
                            if (p.getHashId().equals(existingPlacementId)) {
                                matchedPlacement = p;
                                break;
                            }
                        }
                    }

                    if (matchedPlacement == null) {
                        for (SchematicPlacement p : allPlacements) {
                            GroupPlacementKey existingKey = placementToGroupKey.get(p.getHashId());
                            if (existingKey != null && existingKey.equals(key)) {
                                matchedPlacement = p;
                                groupKeyToPlacementId.put(key, p.getHashId());
                                break;
                            }
                        }
                    }

                    if (matchedPlacement == null) {
                        for (SchematicPlacement p : allPlacements) {
                            if (!placementToGroupKey.containsKey(p.getHashId()) &&
                                    p.getName().equalsIgnoreCase(pData.getName()) &&
                                    p.getOrigin().equals(pData.getOrigin())) {
                                matchedPlacement = p;
                                groupKeyToPlacementId.put(key, p.getHashId());
                                placementToGroupKey.put(p.getHashId(), key);
                                break;
                            }
                        }
                    }

                    if (matchedPlacement != null) {
                        boolean isOwner = group.isOwner(myUuid);
                        if (!isOwner) {
                            if (!matchedPlacement.getOrigin().equals(pData.getOrigin())) {
                                matchedPlacement.setOrigin(pData.getOrigin(), DUMMY_STR_CONSUMER);
                            }
                            Rotation rot = parseRotation(pData.getRotation());
                            if (matchedPlacement.getRotation() != rot) {
                                matchedPlacement.setRotation(rot, DUMMY_MSG_CONSUMER);
                            }
                            Mirror mir = parseMirror(pData.getMirror());
                            if (matchedPlacement.getMirror() != mir) {
                                matchedPlacement.setMirror(mir, DUMMY_MSG_CONSUMER);
                            }
                            if (!matchedPlacement.isLocked()) {
                                matchedPlacement.toggleLocked();
                            }
                        } else {
                            if (matchedPlacement.isLocked() != pData.isLocked()) {
                                matchedPlacement.toggleLocked();
                            }
                        }
                    } else {
                        // Create placement for this group placement
                        loadAndCreatePlacementForGroup(group.getId(), pData, !group.isOwner(myUuid));
                    }
                }
            }
        }

        // Clean up any placements that are no longer part of active joined group placements
        Iterator<Map.Entry<GroupPlacementKey, UUID>> it = groupKeyToPlacementId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GroupPlacementKey, UUID> entry = it.next();
            if (!activeKeys.contains(entry.getKey())) {
                UUID pId = entry.getValue();
                placementToGroupKey.remove(pId);
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

    private void loadAndCreatePlacementForGroup(String groupId, GroupPlacementData pData, boolean lock) {
        String schemId = pData.getSchematicId();
        if (schemId == null || schemId.isEmpty()) return;

        Path localFile = ClientSchematicManager.getInstance().getValidLocalFilePath(schemId);
        if (localFile != null && Files.exists(localFile)) {
            createPlacementFromPath(groupId, pData, localFile, lock);
        } else {
            ClientSchematicManager.getInstance().downloadSchematic(schemId, path -> {
                Path downloaded = ClientSchematicManager.getInstance().getValidLocalFilePath(schemId);
                if (downloaded != null && Files.exists(downloaded)) {
                    createPlacementFromPath(groupId, pData, downloaded, lock);
                }
            });
        }
    }

    private void createPlacementFromPath(String groupId, GroupPlacementData pData, Path path, boolean lock) {
        try {
            LitematicaSchematic schematic = ClientSchematicManager.getInstance().loadSchematicFromFile(path);
            if (schematic == null) return;

            UUID placementId = UUID.randomUUID();
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, pData.getOrigin(), pData.getName(), true, true, placementId);
            placement.setRotation(parseRotation(pData.getRotation()), DUMMY_MSG_CONSUMER);
            placement.setMirror(parseMirror(pData.getMirror()), DUMMY_MSG_CONSUMER);
            if (lock && !placement.isLocked()) {
                placement.toggleLocked();
            }

            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
            GroupPlacementKey key = new GroupPlacementKey(groupId, pData.getId());
            groupKeyToPlacementId.put(key, placement.getHashId());
            placementToGroupKey.put(placement.getHashId(), key);
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
