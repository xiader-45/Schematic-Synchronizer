package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.PlayerPlacementInfo;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

public class WidgetServerBrowserEntry extends WidgetListEntryBase<ServerBrowserEntry> {
    private static long lastClickTime = 0;
    private static ServerBrowserEntry lastClickedEntry = null;

    private final boolean isOdd;
    private final WidgetListServerBrowser parentList;

    public WidgetServerBrowserEntry(int x, int y, int width, int height,
                                    ServerBrowserEntry entry, int listIndex,
                                    WidgetListServerBrowser parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent event, boolean isDouble) {
        if (event.input() != 0) {
            return false;
        }

        if (this.entry.isUp()) {
            this.parentList.goUp();
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDoubleClick = isDouble || (lastClickedEntry == this.entry && (now - lastClickTime) < 500);

        if (this.entry.isDirectory()) {
            if (isDoubleClick) {
                lastClickTime = 0;
                lastClickedEntry = null;
                this.parentList.enterDirectory(this.entry.getFullPath());
                return true;
            } else {
                lastClickTime = now;
                lastClickedEntry = this.entry;
                this.parentList.setLastSelectedEntry(this.entry, this.listIndex);
                this.parentList.getParentGui().onSelectionChange(this.entry);
                return true;
            }
        }

        if (this.entry.isSchematic()) {
            this.parentList.setLastSelectedEntry(this.entry, this.listIndex);
            this.parentList.getParentGui().onSelectionChange(this.entry);
            if (isDoubleClick) {
                this.parentList.getParentGui().onSchematicDoubleClicked(this.entry);
                lastClickTime = 0;
                lastClickedEntry = null;
            } else {
                lastClickTime = now;
                lastClickedEntry = this.entry;
            }
            return true;
        }

        return super.onMouseClickedImpl(event, isDouble);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        // Native Litematica row backgrounds: selected has both highlight and outline
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

        // Entry icon
        IGuiIcon icon = this.entry.getIcon();
        if (icon != null) {
            int iconY = this.y + (this.height - icon.getHeight()) / 2;
            icon.renderAt(ctx, this.x + 3, iconY, 0, true, false);
        }

        int textX = this.x + 22;
        int textY = this.y + (this.height - this.fontHeight) / 2 + 1;

        if (this.entry.isUp()) {
            this.drawString(ctx, textX, textY, 0xFFAAAAAA, "..");
        } else if (this.entry.isDirectory()) {
            String dirText = this.entry.getName() + "/";
            this.drawString(ctx, textX, textY, 0xFFFFFF55, dirText);

            String countStr = "(" + this.entry.getFileCount() + ")";
            int cw = this.getStringWidth(countStr);
            this.drawString(ctx, this.x + this.width - cw - 6, textY, 0xFFAAAAAA, countStr);
        } else if (this.entry.isSchematic()) {
            this.drawString(ctx, textX, textY, 0xFFFFFFFF, this.entry.getName());

            int rightX = this.x + this.width - 6;

            // Cached badge
            if (this.entry.isCached()) {
                String check = "✔";
                int cw = this.getStringWidth(check);
                rightX -= cw;
                this.drawString(ctx, rightX, textY, 0xFF55FF55, check);
                rightX -= 4;
            }

            // Player placements badge
            List<PlayerPlacementInfo> placements = this.entry.getPlacements();
            if (!placements.isEmpty()) {
                String badge = "👤 " + placements.size();
                int bw = this.getStringWidth(badge);
                rightX -= bw;
                this.drawString(ctx, rightX, textY, 0xFF55FFFF, badge);
            }
        }

        super.render(ctx, mouseX, mouseY, selected);
    }
}
