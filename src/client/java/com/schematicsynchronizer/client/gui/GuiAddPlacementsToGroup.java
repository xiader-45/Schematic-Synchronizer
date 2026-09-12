package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public class GuiAddPlacementsToGroup extends GuiListBase<SchematicPlacement, WidgetAddPlacementEntry, WidgetListAddPlacements> {
    private final HologramGroupData group;
    private final Set<UUID> selectedPlacementIds = new HashSet<>();
    private final Set<String> existingSchematicOrNames = new HashSet<>();

    public GuiAddPlacementsToGroup(Screen parent, HologramGroupData group) {
        super(10, 26);
        this.setParent(parent);
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.add_placements.title", group != null ? group.getName() : "");

        if (group != null) {
            for (GroupPlacementData p : group.getPlacements()) {
                existingSchematicOrNames.add(p.getName().toLowerCase(Locale.ROOT));
            }
        }
    }

    public boolean isAlreadyInGroup(SchematicPlacement placement) {
        if (placement == null) return false;
        if (existingSchematicOrNames.contains(placement.getName().toLowerCase(Locale.ROOT))) return true;
        HologramGroupData currentGroup = ClientHologramGroupManager.getInstance().getGroupForPlacement(placement);
        return currentGroup != null && this.group != null && currentGroup.getId().equals(this.group.getId());
    }

    public boolean isSelected(SchematicPlacement placement) {
        return placement != null && selectedPlacementIds.contains(placement.getHashId());
    }

    public void toggleSelection(SchematicPlacement placement) {
        if (placement == null || isAlreadyInGroup(placement)) return;
        UUID id = placement.getHashId();
        if (selectedPlacementIds.contains(id)) {
            selectedPlacementIds.remove(id);
        } else {
            selectedPlacementIds.add(id);
        }
        this.reCreateButtons();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reCreateButtons();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    private void createButtons() {
        int y = this.height - 24;
        int x = 10;

        int selectedCount = selectedPlacementIds.size();

        // Button 1: Add Selected (Count)
        String addLabel = StringUtils.translate("schematic_synchronizer.gui.add_placements.add_selected", selectedCount);
        int addW = this.getStringWidth(addLabel) + 20;
        ButtonGeneric btnAdd = new ButtonGeneric(x, y, addW, 20, addLabel);
        btnAdd.setEnabled(selectedCount > 0 && this.group != null);
        addButton(btnAdd, (btn, mouse) -> {
            if (this.group != null && !selectedPlacementIds.isEmpty()) {
                List<GroupPlacementData> list = new ArrayList<>();
                for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
                    if (selectedPlacementIds.contains(p.getHashId())) {
                        String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(p);
                        if (schemId == null || schemId.isEmpty()) {
                            if (p.getSchematic() != null && p.getSchematic().getFile() != null) {
                                schemId = p.getSchematic().getFile().getFileName().toString();
                            }
                        }
                        if (schemId == null) schemId = "";
                        String pId = UUID.randomUUID().toString().substring(0, 8);
                        list.add(new GroupPlacementData(
                                pId,
                                p.getName(),
                                schemId,
                                p.getOrigin(),
                                p.getRotation().name(),
                                p.getMirror().name(),
                                p.isLocked()
                        ));
                    }
                }
                ClientHologramGroupManager.getInstance().addPlacementsToGroup(this.group.getId(), list);
                GuiBase.openGui(this.getParent());
            }
        });
        x += addW + 4;

        // Button 2: Select All
        String allLabel = StringUtils.translate("schematic_synchronizer.gui.add_placements.select_all");
        int allW = this.getStringWidth(allLabel) + 16;
        ButtonGeneric btnAll = new ButtonGeneric(x, y, allW, 20, allLabel);
        addButton(btnAll, (btn, mouse) -> {
            for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
                if (!isAlreadyInGroup(p)) {
                    selectedPlacementIds.add(p.getHashId());
                }
            }
            this.reCreateButtons();
        });
        x += allW + 4;

        // Button 3: Deselect All
        String noneLabel = StringUtils.translate("schematic_synchronizer.gui.add_placements.deselect_all");
        int noneW = this.getStringWidth(noneLabel) + 16;
        ButtonGeneric btnNone = new ButtonGeneric(x, y, noneW, 20, noneLabel);
        btnNone.setEnabled(selectedCount > 0);
        addButton(btnNone, (btn, mouse) -> {
            selectedPlacementIds.clear();
            this.reCreateButtons();
        });
        x += noneW + 4;

        // Button 4: Cancel (Right aligned)
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = this.getStringWidth(cancelLabel) + 20;
        int cancelX = this.width - cancelW - 10;
        ButtonGeneric btnCancel = new ButtonGeneric(cancelX, y, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> GuiBase.openGui(this.getParent()));
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 56;
    }

    @Override
    protected WidgetListAddPlacements createListWidget(int listX, int listY) {
        return new WidgetListAddPlacements(listX, listY, getBrowserWidth(), getBrowserHeight(), this, null);
    }
}
