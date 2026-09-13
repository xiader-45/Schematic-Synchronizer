package com.schematicsynchronizer.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import com.schematicsynchronizer.network.*;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.gui.Message;

public class ClientHologramGroupManager {
    private static final ClientHologramGroupManager INSTANCE = new ClientHologramGroupManager();
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("schematic_synchronizer_client_settings.json");

    private final List<HologramGroupData> groups = new ArrayList<>();
    private String currentDimension = "minecraft:overworld";
    private boolean canOpManage = false;
    private Runnable guiRefreshCallback;
    private boolean isSyncingFromServer = false;

    private final Map<String, Boolean> groupCustomVisibility = new ConcurrentHashMap<>();
    private boolean defaultCustomVisibility = false;
    private boolean removePlacementsOnLeave = false;

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

    private ClientHologramGroupManager() {
        loadClientSettings();
    }

    public static ClientHologramGroupManager getInstance() {
        return INSTANCE;
    }

    private void loadClientSettings() {
        try {
            if (Files.exists(SETTINGS_FILE)) {
                try (Reader reader = Files.newBufferedReader(SETTINGS_FILE)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj.has("default_custom_visibility")) {
                        this.defaultCustomVisibility = obj.get("default_custom_visibility").getAsBoolean();
                    }
                    if (obj.has("remove_placements_on_leave")) {
                        this.removePlacementsOnLeave = obj.get("remove_placements_on_leave").getAsBoolean();
                    }
                    if (obj.has("custom_visibility") && obj.get("custom_visibility").isJsonObject()) {
                        JsonObject vis = obj.getAsJsonObject("custom_visibility");
                        for (String key : vis.keySet()) {
                            groupCustomVisibility.put(key, vis.get(key).getAsBoolean());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveClientSettings() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("default_custom_visibility", this.defaultCustomVisibility);
            obj.addProperty("remove_placements_on_leave", this.removePlacementsOnLeave);
            JsonObject vis = new JsonObject();
            for (Map.Entry<String, Boolean> entry : groupCustomVisibility.entrySet()) {
                vis.addProperty(entry.getKey(), entry.getValue());
            }
            obj.add("custom_visibility", vis);
            Path parent = SETTINGS_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            }
        } catch (Exception ignored) {}
    }

    public boolean isCustomVisibilityEnabled(String groupId) {
        if (groupId == null) return this.defaultCustomVisibility;
        return groupCustomVisibility.getOrDefault(groupId, this.defaultCustomVisibility);
    }

    public void setCustomVisibilityEnabled(String groupId, boolean enabled) {
        this.defaultCustomVisibility = enabled;
        for (HologramGroupData g : this.groups) {
            groupCustomVisibility.put(g.getId(), enabled);
            if (!enabled) {
                reconcileGroupVisibility(g.getId());
            }
        }
        if (groupId != null) {
            groupCustomVisibility.put(groupId, enabled);
            if (!enabled) {
                reconcileGroupVisibility(groupId);
            }
        }
        saveClientSettings();
    }

    public boolean isRemovePlacementsOnLeaveEnabled() {
        return this.removePlacementsOnLeave;
    }

    public void setRemovePlacementsOnLeaveEnabled(boolean enabled) {
        this.removePlacementsOnLeave = enabled;
        saveClientSettings();
    }

    public void removeGroupPlacementsFromLitematica(String groupId) {
        if (groupId == null) return;
        SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
        Iterator<Map.Entry<GroupPlacementKey, UUID>> it = groupKeyToPlacementId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GroupPlacementKey, UUID> entry = it.next();
            if (groupId.equals(entry.getKey().groupId())) {
                UUID pId = entry.getValue();
                placementToGroupKey.remove(pId);
                it.remove();

                for (SchematicPlacement p : placementManager.getAllSchematicsPlacements()) {
                    if (p.getHashId().equals(pId)) {
                        placementManager.removeSchematicPlacement(p);
                        break;
                    }
                }
            }
        }
    }

    public void reconcileGroupVisibility(String groupId) {
        if (groupId == null) return;
        HologramGroupData group = getGroupById(groupId);
        if (group == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();
        if (!group.isMember(myUuid)) return;

        SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
        this.isSyncingFromServer = true;
        try {
            for (GroupPlacementData pData : group.getPlacements()) {
                GroupPlacementKey key = new GroupPlacementKey(groupId, pData.getId());
                UUID pId = groupKeyToPlacementId.get(key);
                if (pId != null) {
                    for (SchematicPlacement p : placementManager.getAllSchematicsPlacements()) {
                        if (p.getHashId().equals(pId)) {
                            if (p.isEnabled() != pData.isEnabled()) {
                                p.toggleEnabled();
                            }
                            break;
                        }
                    }
                }
            }
        } finally {
            this.isSyncingFromServer = false;
        }
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

    public void refreshActiveGui() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui != null && mc.gui.screen() != null) {
                if (mc.gui.screen() instanceof com.schematicsynchronizer.client.gui.GuiManageHologramGroup mg) {
                    mg.refreshList();
                } else if (mc.gui.screen() instanceof com.schematicsynchronizer.client.gui.GuiManageGroupMembers mm) {
                    mm.refreshList();
                } else if (mc.gui.screen() instanceof com.schematicsynchronizer.client.gui.GuiHologramGroups hg) {
                    hg.refreshList();
                } else if (mc.gui.screen() instanceof com.schematicsynchronizer.client.gui.GuiSelectGroupForPlacement sg) {
                    sg.initGui();
                } else if (mc.gui.screen() instanceof fi.dy.masa.litematica.gui.GuiSchematicPlacementsList spl) {
                    spl.initGui();
                }
            }
            if (this.guiRefreshCallback != null) {
                this.guiRefreshCallback.run();
            }
        });
    }

    public HologramGroupData getGroupById(String groupId) {
        if (groupId == null) return null;
        for (HologramGroupData g : groups) {
            if (g.getId().equals(groupId)) return g;
        }
        return null;
    }

    public List<HologramGroupData> getGroupsForSchematic(ServerSchematicInfo info) {
        if (info == null) return Collections.emptyList();
        List<HologramGroupData> result = new ArrayList<>();
        for (HologramGroupData group : this.groups) {
            for (GroupPlacementData p : group.getPlacements()) {
                if (info.getId().equalsIgnoreCase(p.getSchematicId()) ||
                    info.getName().equalsIgnoreCase(p.getSchematicId()) ||
                    info.getName().equalsIgnoreCase(p.getName())) {
                    if (!result.contains(group)) {
                        result.add(group);
                    }
                    break;
                }
            }
        }
        return result;
    }

    public SchematicPlacement getPlacementForGroupPlacement(String groupId, String placementId) {
        if (groupId == null || placementId == null) return null;
        GroupPlacementKey key = new GroupPlacementKey(groupId, placementId);
        UUID pId = groupKeyToPlacementId.get(key);
        if (pId == null) return null;
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (p.getHashId().equals(pId)) {
                return p;
            }
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
                String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(placement);
                if (schemId == null && placement.getSchematic() != null && placement.getSchematic().getFile() != null) {
                    schemId = placement.getSchematic().getFile().getFileName().toString();
                }
                boolean originMatch = pData.getOrigin().equals(placement.getOrigin());
                boolean schemMatch = schemId != null && schemId.equalsIgnoreCase(pData.getSchematicId());
                if (originMatch && (schemMatch || pData.getName().equalsIgnoreCase(placement.getName()))) {
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
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        return (myUuid != null && group.isOwner(myUuid)) || (group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(mc.player.getName().getString()));
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
        if (groupId == null) return;
        if (isRemovePlacementsOnLeaveEnabled()) {
            removeGroupPlacementsFromLitematica(groupId);
        }
        if (ClientPlayNetworking.canSend(LeaveHologramGroupPayload.TYPE)) {
            ClientPlayNetworking.send(new LeaveHologramGroupPayload(groupId, action));
        }
        if (action == LeaveHologramGroupPayload.ACTION_DELETE) {
            HologramGroupData toRemove = getGroupById(groupId);
            if (toRemove != null) {
                this.groups.remove(toRemove);
            }
        } else {
            HologramGroupData g = getGroupById(groupId);
            if (g != null && Minecraft.getInstance().player != null) {
                g.removeMember(Minecraft.getInstance().getUser().getProfileId());
            }
        }
        refreshActiveGui();
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

    public void updateMemberPermission(String groupId, UUID memberUuid, String permission) {
        HologramGroupData localGroup = getGroupById(groupId);
        if (localGroup != null) {
            localGroup.setMemberPermission(memberUuid, permission);
        }
        if (ClientPlayNetworking.canSend(UpdateMemberPermissionPayload.TYPE)) {
            ClientPlayNetworking.send(new UpdateMemberPermissionPayload(groupId, memberUuid, permission));
        }
        refreshActiveGui();
    }

    public void onPlacementModified(SchematicPlacement placement) {
        if (this.isSyncingFromServer || placement == null) return;
        GroupPlacementKey key = placementToGroupKey.get(placement.getHashId());
        if (key == null) return;

        HologramGroupData group = getGroupById(key.groupId());
        if (group == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        GroupPlacementData pData = group.getPlacement(key.placementId());

        boolean isOwner = (myUuid != null && group.isOwner(myUuid)) || (group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(mc.player.getName().getString()));
        boolean canManage = isOwner || canOpManage;
        boolean canEdit = canManage;
        if (!canEdit) {
            String perm = group.getMemberPermission(myUuid);
            if (HologramGroupData.PERM_ALL.equals(perm) || HologramGroupData.PERM_EDIT.equals(perm)) {
                canEdit = true;
            }
        }

        boolean customVis = isCustomVisibilityEnabled(group.getId());

        if (canEdit) {
            boolean canChangeVis = canManage || customVis;
            if (!canChangeVis && pData != null && placement.isEnabled() != pData.isEnabled()) {
                this.isSyncingFromServer = true;
                placement.toggleEnabled();
                this.isSyncingFromServer = false;
            }
            // Owner / OP / Member with edit rights is modifying: send update to server
            if (ClientPlayNetworking.canSend(UpdateHologramPlacementPayload.TYPE)) {
                ClientPlayNetworking.send(new UpdateHologramPlacementPayload(
                        group.getId(),
                        key.placementId(),
                        placement.getOrigin(),
                        placement.getRotation().name(),
                        placement.getMirror().name(),
                        placement.isLocked(),
                        (pData != null && !canChangeVis) ? pData.isEnabled() : placement.isEnabled()
                ));
            }
        } else {
            // Non-editor attempted to modify: immediately revert to group values!
            if (pData != null) {
                this.isSyncingFromServer = true;
                placement.setOrigin(pData.getOrigin(), DUMMY_STR_CONSUMER);
                placement.setRotation(parseRotation(pData.getRotation()), DUMMY_MSG_CONSUMER);
                placement.setMirror(parseMirror(pData.getMirror()), DUMMY_MSG_CONSUMER);
                if (!customVis && placement.isEnabled() != pData.isEnabled()) {
                    placement.toggleEnabled();
                }
                this.isSyncingFromServer = false;
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

        refreshActiveGui();
    }

    public void checkAndDownloadMissingGroupSchematics() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

        for (HologramGroupData group : this.groups) {
            boolean isOwner = (myUuid != null && group.isOwner(myUuid)) || (group.getOwnerName() != null && mc.player != null && group.getOwnerName().equalsIgnoreCase(mc.player.getName().getString()));
            boolean isMember = (myUuid != null && group.isMember(myUuid)) || isOwner;
            if (isMember) {
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
        
        this.isSyncingFromServer = true;
        try {
            for (HologramGroupData group : this.groups) {
                boolean isOwner = (myUuid != null && group.isOwner(myUuid)) || (group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(mc.player.getName().getString()));
                boolean isMember = (myUuid != null && group.isMember(myUuid)) || isOwner;
    
                if (isMember) {
                    boolean canEdit = isOwner || canOpManage;
                    if (!canEdit) {
                        String perm = group.getMemberPermission(myUuid);
                        if (HologramGroupData.PERM_ALL.equals(perm) || HologramGroupData.PERM_EDIT.equals(perm)) {
                            canEdit = true;
                        }
                    }
                    boolean customVis = isCustomVisibilityEnabled(group.getId());

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
                            if (matchedPlacement.isLocked() != pData.isLocked()) {
                                matchedPlacement.toggleLocked();
                            }
                            if (!customVis && matchedPlacement.isEnabled() != pData.isEnabled()) {
                                matchedPlacement.toggleEnabled();
                            }
                        } else {
                            // Create placement for this group placement
                            loadAndCreatePlacementForGroup(group.getId(), pData, !isOwner);
                        }
                    }
                }
            }
        } finally {
            this.isSyncingFromServer = false;
        }

        // Clean up group mappings for placements that are no longer part of active joined group placements.
        // Retain existing placements in Litematica when a group is deleted or left so user placements are not deleted.
        Iterator<Map.Entry<GroupPlacementKey, UUID>> it = groupKeyToPlacementId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GroupPlacementKey, UUID> entry = it.next();
            if (!activeKeys.contains(entry.getKey())) {
                UUID pId = entry.getValue();
                placementToGroupKey.remove(pId);
                it.remove();

                if (isRemovePlacementsOnLeaveEnabled()) {
                    for (SchematicPlacement p : placementManager.getAllSchematicsPlacements()) {
                        if (p.getHashId().equals(pId)) {
                            placementManager.removeSchematicPlacement(p);
                            break;
                        }
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
        }
    }

    private void createPlacementFromPath(String groupId, GroupPlacementData pData, Path path, boolean lock) {
        try {
            GroupPlacementKey key = new GroupPlacementKey(groupId, pData.getId());
            if (groupKeyToPlacementId.containsKey(key)) {
                return;
            }
            SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
            for (SchematicPlacement existing : placementManager.getAllSchematicsPlacements()) {
                GroupPlacementKey existingKey = placementToGroupKey.get(existing.getHashId());
                if (key.equals(existingKey)) {
                    groupKeyToPlacementId.put(key, existing.getHashId());
                    return;
                }
                if (!placementToGroupKey.containsKey(existing.getHashId()) &&
                        existing.getName().equalsIgnoreCase(pData.getName()) &&
                        existing.getOrigin().equals(pData.getOrigin())) {
                    groupKeyToPlacementId.put(key, existing.getHashId());
                    placementToGroupKey.put(existing.getHashId(), key);
                    return;
                }
            }

            LitematicaSchematic schematic = ClientSchematicManager.getInstance().loadSchematicFromFile(path);
            if (schematic == null) return;

            UUID placementId = UUID.randomUUID();
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, pData.getOrigin(), pData.getName(), true, true, placementId);
            placement.setRotation(parseRotation(pData.getRotation()), DUMMY_MSG_CONSUMER);
            placement.setMirror(parseMirror(pData.getMirror()), DUMMY_MSG_CONSUMER);
            if (lock && !placement.isLocked()) {
                placement.toggleLocked();
            }
            if (placement.isEnabled() != pData.isEnabled()) {
                placement.toggleEnabled();
            }

            placementManager.addSchematicPlacement(placement, true);
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
