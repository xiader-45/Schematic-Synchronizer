package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.GroupPlacementData;
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

    public GuiCreateHologramGroup(Screen parent, SchematicPlacement initialPlacement) {
        this.setParent(parent);
        this.initialPlacement = initialPlacement;
        this.title = StringUtils.translate("schematic_synchronizer.gui.create_group.title");
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogW = 320;
        int dialogH = 120;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String defaultName = "";
        if (this.initialPlacement != null) {
            defaultName = this.initialPlacement.getName();
        }
        if (defaultName == null || defaultName.trim().isEmpty()) {
            int count = ClientHologramGroupManager.getInstance().getGroups().size() + 1;
            defaultName = StringUtils.translate("schematic_synchronizer.gui.create_group.default_name", count);
        }

        this.nameField = new GuiTextFieldGeneric(x + 14, y + 46, dialogW - 28, 20, this.font);
        this.nameField.setTextWrapper(defaultName);
        this.addTextField(this.nameField, null);

        int btnY = y + dialogH - 32;

        // Button: Create Group
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.create_group.create_button");
        int createW = this.getStringWidth(createLabel) + 16;
        ButtonGeneric btnCreate = new ButtonGeneric(x + 14, btnY, createW, 20, createLabel);
        addButton(btnCreate, (btn, mouse) -> {
            String name = this.nameField.getTextWrapper().trim();
            if (!name.isEmpty()) {
                List<GroupPlacementData> initialList = new ArrayList<>();
                if (this.initialPlacement != null) {
                    String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.initialPlacement);
                    if (schemId == null || schemId.isEmpty()) {
                        if (this.initialPlacement.getSchematic() != null && this.initialPlacement.getSchematic().getFile() != null) {
                            schemId = this.initialPlacement.getSchematic().getFile().getFileName().toString();
                        }
                    }
                    if (schemId == null) schemId = "";
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
        int dialogH = 120;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 320;
        int dialogH = 120;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.create_group.title");
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 12, 0xFFFFAA00);

        this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.create_group.field.name"), x + 14, y + 32, 0xFFAAAAAA);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
