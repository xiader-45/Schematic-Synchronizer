package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WidgetServerBrowserEntry extends WidgetListEntryBase<ServerBrowserEntry> {
    private static final Identifier ICON_DOWNLOAD = SchematicSynchronizer.id("textures/gui/download_icon.png");
    private static final Identifier ICON_GROUP = SchematicSynchronizer.id("textures/gui/group_icon.png");
    private static final Identifier ICON_GROUP_HOVER = SchematicSynchronizer.id("textures/gui/group_icon_highlight.png");

    private static long lastClickTime = 0;
    private static ServerBrowserEntry lastClickedEntry = null;

    private final boolean isOdd;
    private final WidgetListServerBrowser parentList;

    private int dlIconX = -1;
    private int dlIconY = -1;
    private int groupIconX = -1;
    private int groupIconY = -1;
    private List<HologramGroupData> cachedGroupsForHover = Collections.emptyList();

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
            lastClickTime = now;
            lastClickedEntry = this.entry;
            return true;
        }

        return super.onMouseClickedImpl(event, isDouble);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        this.dlIconX = -1;
        this.groupIconX = -1;
        this.cachedGroupsForHover = Collections.emptyList();

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
            int iconY = this.y + (this.height - 11) / 2;

            // 1. Downloaded / Cached badge (download_icon.png)
            if (this.entry.isCached()) {
                rightX -= 11;
                this.dlIconX = rightX;
                this.dlIconY = iconY;
                ctx.blit(RenderPipelines.GUI_TEXTURED, ICON_DOWNLOAD, this.dlIconX, this.dlIconY, 0.0f, 0.0f, 11, 11, 11, 11);
                rightX -= 5;
            }

            // 2. In Hologram Group(s) badge (group_icon.png)
            List<HologramGroupData> groups = ClientHologramGroupManager.getInstance().getGroupsForSchematic(this.entry.getSchematicInfo());
            if (!groups.isEmpty()) {
                this.cachedGroupsForHover = groups;
                rightX -= 11;
                this.groupIconX = rightX;
                this.groupIconY = iconY;

                boolean groupHovered = (mouseX >= this.groupIconX && mouseX < this.groupIconX + 11 &&
                                        mouseY >= this.groupIconY && mouseY < this.groupIconY + 11);
                Identifier groupTex = groupHovered ? ICON_GROUP_HOVER : ICON_GROUP;
                ctx.blit(RenderPipelines.GUI_TEXTURED, groupTex, this.groupIconX, this.groupIconY, 0.0f, 0.0f, 11, 11, 11, 11);
                rightX -= 5;
            }
        }

        super.render(ctx, mouseX, mouseY, selected);
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean isOdd) {
        if (this.entry.isSchematic()) {
            // Hover over download icon
            if (this.dlIconX >= 0 && mouseX >= this.dlIconX && mouseX < this.dlIconX + 11 &&
                    this.dlIconY >= 0 && mouseY >= this.dlIconY && mouseY < this.dlIconY + 11) {
                Path localPath = ClientSchematicManager.getInstance().getValidLocalFilePath(this.entry.getSchematicInfo());
                if (localPath == null) {
                    localPath = ClientSchematicManager.getInstance().getLocalFilePath(this.entry.getSchematicInfo());
                }
                String displayPath = getDisplayPathStartingWithSchematics(localPath);
                String hoverText = StringUtils.translate("schematic_synchronizer.gui.hover.saved_path", displayPath);
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(hoverText));
                return;
            }

            // Hover over group icon
            if (this.groupIconX >= 0 && mouseX >= this.groupIconX && mouseX < this.groupIconX + 11 &&
                    this.groupIconY >= 0 && mouseY >= this.groupIconY && mouseY < this.groupIconY + 11) {
                if (!this.cachedGroupsForHover.isEmpty()) {
                    List<String> lines = new ArrayList<>();
                    if (this.cachedGroupsForHover.size() == 1) {
                        lines.add(StringUtils.translate("schematic_synchronizer.gui.hover.in_group_single", this.cachedGroupsForHover.get(0).getName()));
                    } else {
                        lines.add(StringUtils.translate("schematic_synchronizer.gui.hover.in_group_multiple"));
                        for (HologramGroupData g : this.cachedGroupsForHover) {
                            lines.add("  §e• §f" + g.getName());
                        }
                    }
                    RenderUtils.drawHoverText(ctx, mouseX, mouseY, lines);
                    return;
                }
            }
        }

        super.postRenderHovered(ctx, mouseX, mouseY, isOdd);
    }

    private String getDisplayPathStartingWithSchematics(Path localPath) {
        if (localPath == null) return "schematics";
        try {
            Path schemDir = FabricLoader.getInstance().getGameDir().resolve("schematics").toAbsolutePath().normalize();
            Path absLocal = localPath.toAbsolutePath().normalize();
            if (absLocal.startsWith(schemDir)) {
                String rel = schemDir.relativize(absLocal).toString().replace('\\', '/');
                return rel.isEmpty() ? "schematics" : "schematics/" + rel;
            }
            Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            if (absLocal.startsWith(gameDir)) {
                return gameDir.relativize(absLocal).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return "schematics/" + localPath.getFileName().toString();
    }
}
