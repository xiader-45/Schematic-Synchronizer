package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public class GuiCreateHologramGroup extends GuiBase {
    private final SchematicPlacement placement;
    private final ServerSchematicInfo schematicInfo;
    private GuiTextFieldGeneric nameField;
    private GuiTextFieldGeneric schemField;

    public GuiCreateHologramGroup(Screen parent, SchematicPlacement placement, ServerSchematicInfo schematicInfo) {
        this.setParent(parent);
        this.placement = placement;
        this.schematicInfo = schematicInfo;
        this.title = StringUtils.translate("schematic_synchronizer.gui.create_group.title");
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogW = 320;
        int dialogH = 170;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String defaultName = "";
        String defaultSchem = "";

        if (this.placement != null) {
            defaultName = this.placement.getName();
            defaultSchem = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.placement);
        } else if (this.schematicInfo != null) {
            defaultName = this.schematicInfo.getName();
            defaultSchem = this.schematicInfo.getName();
        }

        if (defaultSchem == null) defaultSchem = "";
        if (defaultName == null || defaultName.isEmpty()) {
            defaultName = defaultSchem.endsWith(".litematic")
                    ? defaultSchem.substring(0, defaultSchem.length() - 10)
                    : defaultSchem;
        }

        // Group Name field
        this.nameField = new GuiTextFieldGeneric(x + 14, y + 42, dialogW - 28, 20, this.font);
        this.nameField.setTextWrapper(defaultName);
        this.addTextField(this.nameField, null);

        // Schematic ID field
        this.schemField = new GuiTextFieldGeneric(x + 14, y + 90, dialogW - 28, 20, this.font);
        this.schemField.setTextWrapper(defaultSchem);
        this.addTextField(this.schemField, null);

        int btnY = y + dialogH - 30;

        // Button: Create
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.create_group.create_button");
        int createW = 110;
        ButtonGeneric btnCreate = new ButtonGeneric(x + 14, btnY, createW, 20, createLabel);
        addButton(btnCreate, (btn, mouse) -> {
            String name = this.nameField.getTextWrapper().trim();
            String schem = this.schemField.getTextWrapper().trim();
            if (!name.isEmpty() && !schem.isEmpty()) {
                BlockPos origin = BlockPos.ZERO;
                Rotation rot = Rotation.NONE;
                Mirror mir = Mirror.NONE;

                if (this.placement != null) {
                    origin = this.placement.getOrigin();
                    rot = this.placement.getRotation();
                    mir = this.placement.getMirror();
                } else {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        origin = mc.player.blockPosition();
                    }
                }

                ClientHologramGroupManager.getInstance().createGroup(name, schem, origin, rot, mir);
                closeGui(true);
            }
        });

        // Button: Cancel
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = 80;
        ButtonGeneric btnCancel = new ButtonGeneric(x + dialogW - cancelW - 14, btnY, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> closeGui(true));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 320;
        int dialogH = 170;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);

        String titleStr = "\u00a76\u00a7l" + StringUtils.translate("schematic_synchronizer.gui.create_group.title");
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 12, 0xFFFFAA00);

        this.drawString(ctx, "\u00a77" + StringUtils.translate("schematic_synchronizer.gui.create_group.field.name"), x + 14, y + 30, 0xFFAAAAAA);
        this.drawString(ctx, "\u00a77" + StringUtils.translate("schematic_synchronizer.gui.create_group.field.schematic"), x + 14, y + 78, 0xFFAAAAAA);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
