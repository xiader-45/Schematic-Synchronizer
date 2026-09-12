package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.core.BlockPos;

public class WidgetGroupPlacementEntry extends WidgetListEntryBase<GroupPlacementData> {
    private final boolean isOdd;
    private final WidgetListGroupPlacements parentList;

    public WidgetGroupPlacementEntry(int x, int y, int width, int height,
                                    GroupPlacementData entry, int listIndex,
                                    WidgetListGroupPlacements parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;

        HologramGroupData group = parentList.getParentGui().getGroup();
        boolean canManage = parentList.getParentGui().canManage();

        if (canManage && group != null && entry != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.remove_placement");
            int btnW = 60;
            int btnH = 20;
            ButtonGeneric btnRemove = new ButtonGeneric(x + width - btnW - 4, y + (height - btnH) / 2, btnW, btnH, delLabel);
            btnRemove.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.remove_placement"));
            this.addButton(btnRemove, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().removePlacementFromGroup(group.getId(), entry.getId());
                parentList.getParentGui().refreshList();
            });
        }
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (selected) {
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

        if (this.entry == null) return;

        IGuiIcon icon = Icons.FILE_ICON_LITEMATIC;
        if (icon != null) {
            int iconY = this.y + (this.height - icon.getHeight()) / 2;
            icon.renderAt(ctx, this.x + 4, iconY, 0, true, false);
        }

        int textX = this.x + 22;
        int textY = this.y + 3;

        // Line 1: Name and schematic file
        String nameLine = "§e" + this.entry.getName() + " §7(" + this.entry.getSchematicId() + ")";
        if (this.entry.isLocked()) {
            nameLine += " §c[§4" + StringUtils.translate("schematic_synchronizer.gui.manage_group.locked") + "§c]";
        }
        this.drawString(ctx, textX, textY, 0xFFFFFFFF, nameLine);

        // Line 2: Origin, rotation, mirror
        BlockPos pos = this.entry.getOrigin();
        String posStr = String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
        String detailLine = "§8" + posStr + "  §7|  §8Rot: §7" + this.entry.getRotation() + "  §7|  §8Mir: §7" + this.entry.getMirror();
        this.drawString(ctx, textX, textY + 11, 0xFFAAAAAA, detailLine);

        super.render(ctx, mouseX, mouseY, selected);
    }
}
