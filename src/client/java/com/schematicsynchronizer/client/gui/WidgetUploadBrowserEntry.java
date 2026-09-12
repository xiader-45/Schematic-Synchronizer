package com.schematicsynchronizer.client.gui;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.input.MouseButtonEvent;

public class WidgetUploadBrowserEntry extends WidgetListEntryBase<LocalFileEntry> {
    private static long lastClickTime = 0;
    private static LocalFileEntry lastClickedEntry = null;

    private final WidgetListUploadBrowser parentList;
    private final boolean isOdd;

    public WidgetUploadBrowserEntry(int x, int y, int width, int height, LocalFileEntry entry, int listIndex, WidgetListUploadBrowser parentList) {
        super(x, y, width, height, entry, listIndex);
        this.parentList = parentList;
        this.isOdd = (listIndex % 2 != 0);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean isSelected) {
        boolean selected = this.parentList.isEntrySelected(this.entry);
        boolean hovered = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;

        // Background
        if (selected) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x70FFFFFF);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFFFFFFFF);
        } else if (hovered) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x40FFFFFF);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xA0FFFFFF);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x10FFFFFF);
        }

        int textY = this.y + (this.height - this.fontHeight) / 2 + 1;
        int iconY = this.y + (this.height - 12) / 2;
        int iconX = this.x + 3;

        if (this.entry.isUp()) {
            Icons.FILE_ICON_DIR_UP.renderAt(ctx, iconX, iconY, 0, false, false);
            this.drawString(ctx, iconX + 16, textY, 0xFFFFFF55, "§e..");
        } else if (this.entry.isDirectory()) {
            Icons.FILE_ICON_DIR.renderAt(ctx, iconX, iconY, 0, false, false);
            String dirText = "§6" + this.entry.getName() + "/";
            this.drawString(ctx, iconX + 16, textY, 0xFFFFAA00, dirText);

            String countStr = "§7(" + this.entry.getFileCount() + ")";
            int countW = this.getStringWidth(countStr);
            this.drawString(ctx, this.x + this.width - countW - 6, textY, 0xFFAAAAAA, countStr);
        } else {
            String lower = this.entry.getName().toLowerCase();
            if (lower.endsWith(".litematic")) {
                Icons.FILE_ICON_LITEMATIC.renderAt(ctx, iconX, iconY, 0, false, false);
            } else {
                Icons.FILE_ICON_SCHEMATIC.renderAt(ctx, iconX, iconY, 0, false, false);
            }

            String name = this.entry.getName();
            int color = selected ? 0xFFFFFFFF : 0xFFDDDDDD;
            this.drawString(ctx, iconX + 16, textY, color, name);

            // Size text on right
            String sizeStr = "§7" + this.entry.getFormattedSize();
            int sizeW = this.getStringWidth(sizeStr);
            this.drawString(ctx, this.x + this.width - sizeW - 6, textY, 0xFFAAAAAA, sizeStr);
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent event, boolean isDouble) {
        if (event.input() != 0) { // Left click only
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
                this.parentList.enterDirectory(this.entry.getPath());
                return true;
            } else {
                lastClickTime = now;
                lastClickedEntry = this.entry;
                boolean shift = GuiBase.isShiftDown();
                boolean ctrl = GuiBase.isCtrlDown();
                this.parentList.handleEntrySelection(this.entry, shift || ctrl);
                return true;
            }
        }

        // Regular file selection
        lastClickTime = now;
        lastClickedEntry = this.entry;
        boolean shift = GuiBase.isShiftDown();
        boolean ctrl = GuiBase.isCtrlDown();
        this.parentList.handleEntrySelection(this.entry, shift || ctrl);
        return true;
    }
}
