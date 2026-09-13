package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.GuiSchematicPlacementsList;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiCreateHologramGroup extends GuiBase {
    private final SchematicPlacement initialPlacement;
    private GuiTextFieldGeneric nameField;
    private ButtonGeneric btnCreate;

    public GuiCreateHologramGroup(Screen parent, SchematicPlacement initialPlacement) {
        this.setParent(parent);
        this.initialPlacement = initialPlacement;
        this.title = StringUtils.translate("schematic_synchronizer.gui.create_group.title");
    }

    private boolean isGroupNameTaken(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String clean = name.trim();
        for (HologramGroupData g : ClientHologramGroupManager.getInstance().getGroups()) {
            if (g.getName().equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    private String getUniqueDefaultName(String base) {
        String name = base;
        int counter = 1;
        while (isGroupNameTaken(name)) {
            name = base + " " + (++counter);
        }
        return name;
    }

    @Override
    protected void closeGui(boolean openParent) {
        if (openParent) {
            if (this.getParent() != null) {
                GuiBase.openGui(this.getParent());
            } else {
                GuiBase.openGui(new GuiSchematicPlacementsList());
            }
        } else {
            super.closeGui(false);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogW = 320;
        int dialogH = 130;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String defaultName = "";
        if (this.initialPlacement != null) {
            defaultName = getUniqueDefaultName(this.initialPlacement.getName());
        } else {
            int existingCount = ClientHologramGroupManager.getInstance().getGroups().size();
            defaultName = getUniqueDefaultName(StringUtils.translate("schematic_synchronizer.gui.create_group.default_name", existingCount + 1));
        }

        this.nameField = new GuiTextFieldGeneric(x + 14, y + 44, dialogW - 28, 20, this.font);
        this.nameField.setTextWrapper(defaultName);
        this.nameField.setFocused(true);
        this.addTextField(this.nameField, null);

        int btnY = y + dialogH - 28;

        // Button: Create
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.create_group.create_button");
        int createW = this.getStringWidth(createLabel) + 20;
        this.btnCreate = new ButtonGeneric(x + 14, btnY, createW, 20, createLabel);
        addButton(this.btnCreate, (btn, mouse) -> {
            String name = this.nameField.getTextWrapper().trim();
            if (!name.isEmpty() && !isGroupNameTaken(name)) {
                List<GroupPlacementData> initialList = new ArrayList<>();
                if (this.initialPlacement != null) {
                    String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.initialPlacement);
                    if (schemId == null || schemId.isEmpty()) {
                        if (this.initialPlacement.getSchematic() != null && this.initialPlacement.getSchematic().getFile() != null) {
                            schemId = this.initialPlacement.getSchematic().getFile().getFileName().toString();
                        }
                    }
                    if (schemId == null) schemId = "";
                    ClientSchematicManager.getInstance().ensureSchematicUploaded(this.initialPlacement, schemId);
                    String pId = UUID.randomUUID().toString().substring(0, 8);
                    initialList.add(new GroupPlacementData(
                            pId,
                            this.initialPlacement.getName(),
                            schemId,
                            this.initialPlacement.getOrigin(),
                            this.initialPlacement.getRotation().name(),
                            this.initialPlacement.getMirror().name(),
                            this.initialPlacement.isLocked()
                    ));
                }

                ClientHologramGroupManager.getInstance().createGroup(name, initialList);
                closeGui(true);
            }
        });

        // Button: Cancel
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = this.getStringWidth(cancelLabel) + 20;
        ButtonGeneric btnCancel = new ButtonGeneric(x + dialogW - cancelW - 14, btnY, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> closeGui(true));
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 320;
        int dialogH = 130;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFFFFF);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 320;
        int dialogH = 130;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String titleStr = StringUtils.translate("schematic_synchronizer.gui.create_group.title");
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 12, 0xFFFFFFFF);

        this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.create_group.field.name"), x + 14, y + 32, 0xFFAAAAAA);

        String entered = this.nameField != null ? this.nameField.getTextWrapper().trim() : "";
        boolean isDuplicate = isGroupNameTaken(entered);
        boolean isEmpty = entered.isEmpty();

        if (this.btnCreate != null) {
            this.btnCreate.setEnabled(!isDuplicate && !isEmpty);
        }

        if (isDuplicate) {
            this.drawString(ctx, "§c" + StringUtils.translate("schematic_synchronizer.gui.create_group.name_exists"), x + 14, y + 70, 0xFFFF5555);
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
