package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.PlayerPlacementInfo;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.input.MouseButtonEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WidgetServerPlacementEntry extends WidgetListEntryBase<PlayerPlacementInfo> {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final boolean isOdd;
    private final WidgetListServerPlacements parentList;

    public WidgetServerPlacementEntry(int x, int y, int width, int height,
                                     PlayerPlacementInfo entry, int listIndex,
                                     WidgetListServerPlacements parentList) {
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
        int textY = this.y + (this.height - this.fontHeight) / 2 + 1;

        if (this.entry != null) {
            String schemName = this.entry.getSchematicId();
            int slash = schemName.lastIndexOf('/');
            if (slash >= 0 && slash < schemName.length() - 1) {
                schemName = schemName.substring(slash + 1);
            }
            if (schemName.endsWith(".litematic")) {
                schemName = schemName.substring(0, schemName.length() - 10);
            }

            String bracketColor = selected ? "§f" : "§7";
            String mainText = schemName + " " + bracketColor + "(§b" + this.entry.getOwnerName() + bracketColor + ")";

            int rightX = this.x + this.width - 6;

            // 1. Rotation (rightmost)
            String rot = this.entry.getRotation();
            if (rot != null && !rot.equals("NONE")) {
                int rw = this.getStringWidth(rot);
                rightX -= rw;
                this.drawString(ctx, rightX, textY, 0xFFFFAA00, rot);
                rightX -= 6;
            }

            // 2. Coordinates (white when selected, light gray otherwise)
            String posStr = this.entry.getPos().toShortString();
            int pw = this.getStringWidth(posStr);
            rightX -= pw;
            int posColor = selected ? 0xFFFFFFFF : 0xFFAAAAAA;
            this.drawString(ctx, rightX, textY, posColor, posStr);
            rightX -= 6;

            // 3. Dimension
            String dim = this.entry.getDimension();
            if (dim.contains(":")) {
                dim = dim.substring(dim.indexOf(':') + 1);
            }
            int dw = this.getStringWidth(dim);
            rightX -= dw;
            this.drawString(ctx, rightX, textY, 0xFF55FF55, dim);

            // 4. Date & Time (white when selected, gray otherwise)
            if (this.entry.getTimestamp() > 0) {
                rightX -= 6;
                String timeStr = DATE_FORMAT.format(new Date(this.entry.getTimestamp()));
                int tw = this.getStringWidth(timeStr);
                rightX -= tw;
                int timeColor = selected ? 0xFFFFFFFF : 0xFF888888;
                this.drawString(ctx, rightX, textY, timeColor, timeStr);
            }

            // Draw main text clamped if needed to avoid overlapping rightX
            int maxMainWidth = rightX - textX - 6;
            if (maxMainWidth > 20) {
                mainText = StringUtils.clampTextToRenderLength(mainText, maxMainWidth, LeftRight.RIGHT, "...");
            }
            this.drawString(ctx, textX, textY, 0xFFFFFFFF, mainText);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }
}
