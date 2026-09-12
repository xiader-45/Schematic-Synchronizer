package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientSchematicManager;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;

public class WidgetAddPlacementEntry extends WidgetListEntryBase<SchematicPlacement> {
    private final boolean isOdd;
    private final WidgetListAddPlacements parentList;
    private final boolean alreadyInGroup;

    public WidgetAddPlacementEntry(int x, int y, int width, int height,
                                   SchematicPlacement entry, int listIndex,
                                   WidgetListAddPlacements parentList, boolean alreadyInGroup) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;
        this.alreadyInGroup = alreadyInGroup;

        if (!alreadyInGroup && entry != null) {
            boolean isSelected = parentList.getParentGui().isSelected(entry);
            ButtonOnOff checkBtn = new ButtonOnOff(x + width - 36, y + 2, 32, true, "", isSelected);
            this.addButton(checkBtn, (btn, mouse) -> {
                parentList.getParentGui().toggleSelection(entry);
                ((ButtonOnOff) btn).updateDisplayString(parentList.getParentGui().isSelected(entry));
            });
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent event, boolean isDouble) {
        if (event.input() != 0) {
            return false;
        }
        if (!alreadyInGroup && this.entry != null) {
            this.parentList.getParentGui().toggleSelection(this.entry);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        boolean isSelected = this.entry != null && this.parentList.getParentGui().isSelected(this.entry);

        if (isSelected) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x60FFAA00);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFFFFAA00);
        } else if (this.isMouseOver(mouseX, mouseY)) {
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xEEEEEEEE);
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        }

        IGuiIcon icon = Icons.FILE_ICON_LITEMATIC;
        if (icon != null) {
            int iconY = this.y + (this.height - icon.getHeight()) / 2;
            icon.renderAt(ctx, this.x + 3, iconY, 0, true, false);
        }

        int textX = this.x + 22;
        int textY = this.y + 2;

        if (this.entry != null) {
            String name = this.entry.getName();
            String titleColor = alreadyInGroup ? "§8" : (isSelected ? "§6§l" : "§f§l");
            String status = alreadyInGroup ? " §7[" + StringUtils.translate("schematic_synchronizer.gui.add_placements.already_in_group") + "]" : "";
            this.drawString(ctx, textX, textY, 0xFFFFFFFF, titleColor + name + status);

            String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.entry);
            if (schemId == null && this.entry.getSchematic() != null && this.entry.getSchematic().getFile() != null) {
                schemId = this.entry.getSchematic().getFile().getFileName().toString();
            }
            if (schemId == null) schemId = "unknown";

            BlockPos pos = this.entry.getOrigin();
            String posStr = String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
            String subText = "§7" + schemId + " §8| §7" + posStr;
            this.drawString(ctx, textX, textY + 11, 0xFFAAAAAA, subText);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }
}
