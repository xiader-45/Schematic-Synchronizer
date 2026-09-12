package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.UUID;

public class WidgetHologramGroupEntry extends WidgetListEntryBase<HologramGroupData> {
    private final boolean isOdd;
    private final WidgetListHologramGroups parentList;

    public WidgetHologramGroupEntry(int x, int y, int width, int height,
                                   HologramGroupData entry, int listIndex,
                                   WidgetListHologramGroups parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent event, boolean isDouble) {
        if (event.input() != 0) {
            return false;
        }
        this.parentList.setLastSelectedEntry(this.entry, this.listIndex);
        this.parentList.getParentGui().onSelectionChange(this.entry);
        return true;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (selected) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x70FFFFFF);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xEEEEEEEE);
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
            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;

            boolean isOwner = (myUuid != null && this.entry.isOwner(myUuid));
            boolean isMember = (myUuid != null && this.entry.isMember(myUuid));

            String titleColor = isOwner ? "§6§l" : (isMember ? "§a§l" : "§f§l");
            String groupName = titleColor + this.entry.getName();

            String statusTag = "";
            if (isOwner) {
                statusTag = " §6[" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + "]";
            } else if (isMember) {
                statusTag = " §a[" + StringUtils.translate("schematic_synchronizer.gui.group.status.member") + "]";
            }

            int nameColor = selected ? 0xFFFFFFFF : (isOwner ? 0xFFFFAA00 : (isMember ? 0xFF55FF55 : 0xFFFFFFFF));
            this.drawString(ctx, textX, textY, nameColor, groupName + statusTag);

            int subColor = selected ? 0xFFFFFFFF : 0xFFAAAAAA;
            int pCount = this.entry.getPlacements().size();
            int mCount = this.entry.getMembers().size();
            String subText = "§7" + StringUtils.translate("schematic_synchronizer.gui.group.placements_count", pCount) +
                    " §8| §7" + StringUtils.translate("schematic_synchronizer.gui.group.members_count", mCount) +
                    " §8| §7" + StringUtils.translate("schematic_synchronizer.gui.group.owner_prefix", this.entry.getOwnerName());
            this.drawString(ctx, textX, textY + 11, subColor, subText);
        }
    }
}
