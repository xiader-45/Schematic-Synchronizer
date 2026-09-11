package com.schematicsynchronizer.client.gui;

import com.google.common.collect.ImmutableList;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.*;

public class WidgetListServerBrowser extends WidgetListBase<ServerBrowserEntry, WidgetServerBrowserEntry> {
    private final GuiServerSchematicsList parent;
    private String currentPath = "";

    public WidgetListServerBrowser(int x, int y, int width, int height,
                                  GuiServerSchematicsList parent,
                                  ISelectionListener<ServerBrowserEntry> selectionListener) {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.browserEntryHeight = 22;

        int searchWidth = Math.min(140, Math.max(80, width / 3));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 4, searchWidth, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.browserEntriesOffsetY = 22;
    }

    public GuiServerSchematicsList getParentGui() {
        return this.parent;
    }

    public WidgetSearchBar getSearchBar() {
        return this.widgetSearchBar;
    }

    public String getCurrentPath() {
        return this.currentPath;
    }

    public void enterDirectory(String path) {
        this.currentPath = path != null ? path : "";
        this.clearSelection();
        this.refreshEntries();
        this.resetScrollbarPosition();
        this.parent.onSelectionChange(null);
    }

    public void goUp() {
        if (this.currentPath.isEmpty()) return;
        int lastSlash = this.currentPath.lastIndexOf('/');
        if (lastSlash > 0) {
            this.currentPath = this.currentPath.substring(0, lastSlash);
        } else {
            this.currentPath = "";
        }
        this.clearSelection();
        this.refreshEntries();
        this.resetScrollbarPosition();
        this.parent.onSelectionChange(null);
    }

    public void goToRoot() {
        this.currentPath = "";
        this.clearSelection();
        this.refreshEntries();
        this.resetScrollbarPosition();
        this.parent.onSelectionChange(null);
    }

    @Override
    protected List<String> getEntryStringsForFilter(ServerBrowserEntry entry) {
        if (entry.getSchematicInfo() != null) {
            return ImmutableList.of(entry.getName().toLowerCase(), entry.getFullPath().toLowerCase());
        }
        return ImmutableList.of(entry.getName().toLowerCase());
    }

    @Override
    protected WidgetServerBrowserEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ServerBrowserEntry entry) {
        return new WidgetServerBrowserEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<ServerBrowserEntry> getAllEntries() {
        List<ServerBrowserEntry> entries = new ArrayList<>();
        Collection<ServerSchematicInfo> allSchematics = ClientSchematicManager.getInstance().getServerSchematics();

        String filter = this.getFilterText();
        boolean searching = filter != null && !filter.trim().isEmpty();

        if (searching) {
            String searchLower = filter.trim().toLowerCase();
            for (ServerSchematicInfo s : allSchematics) {
                if (s.getName().toLowerCase().contains(searchLower) || s.getId().toLowerCase().contains(searchLower)) {
                    List<PlayerPlacementInfo> placements = ClientSchematicManager.getInstance().getPlacementsForSchematic(s.getId());
                    boolean cached = ClientSchematicManager.getInstance().isDownloaded(s);
                    entries.add(ServerBrowserEntry.createSchematic(s, placements, cached));
                }
            }
            entries.sort(Comparator.comparing(ServerBrowserEntry::getName, String.CASE_INSENSITIVE_ORDER));
            return entries;
        }

        String cur = this.currentPath.isEmpty() ? "" : (this.currentPath.endsWith("/") ? this.currentPath : this.currentPath + "/");

        if (!this.currentPath.isEmpty()) {
            int lastSlash = this.currentPath.lastIndexOf('/');
            String parentPath = (lastSlash > 0) ? this.currentPath.substring(0, lastSlash) : "";
            entries.add(ServerBrowserEntry.createUp(parentPath));
        }

        Map<String, Integer> subDirs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<ServerBrowserEntry> files = new ArrayList<>();

        for (ServerSchematicInfo s : allSchematics) {
            String id = s.getId().replace('\\', '/');
            if (id.startsWith("/")) {
                id = id.substring(1);
            }

            if (cur.isEmpty()) {
                int slashIdx = id.indexOf('/');
                if (slashIdx < 0) {
                    List<PlayerPlacementInfo> placements = ClientSchematicManager.getInstance().getPlacementsForSchematic(s.getId());
                    boolean cached = ClientSchematicManager.getInstance().isDownloaded(s);
                    files.add(ServerBrowserEntry.createSchematic(s, placements, cached));
                } else {
                    String sub = id.substring(0, slashIdx);
                    subDirs.put(sub, subDirs.getOrDefault(sub, 0) + 1);
                }
            } else {
                if (id.startsWith(cur)) {
                    String rem = id.substring(cur.length());
                    int slashIdx = rem.indexOf('/');
                    if (slashIdx < 0) {
                        List<PlayerPlacementInfo> placements = ClientSchematicManager.getInstance().getPlacementsForSchematic(s.getId());
                        boolean cached = ClientSchematicManager.getInstance().isDownloaded(s);
                        files.add(ServerBrowserEntry.createSchematic(s, placements, cached));
                    } else {
                        String sub = rem.substring(0, slashIdx);
                        subDirs.put(sub, subDirs.getOrDefault(sub, 0) + 1);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> dirEntry : subDirs.entrySet()) {
            String dirName = dirEntry.getKey();
            String fullDirPath = cur.isEmpty() ? dirName : cur + dirName;
            entries.add(ServerBrowserEntry.createDirectory(dirName, fullDirPath, dirEntry.getValue()));
        }

        files.sort(Comparator.comparing(ServerBrowserEntry::getName, String.CASE_INSENSITIVE_ORDER));
        entries.addAll(files);

        return entries;
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        // Check root button
        if (mx >= this.posX + 3 && mx <= this.posX + 17 && my >= this.posY + 3 && my <= this.posY + 17) {
            goToRoot();
            return true;
        }

        // Check up button
        if (mx >= this.posX + 20 && mx <= this.posX + 34 && my >= this.posY + 3 && my <= this.posY + 17) {
            goUp();
            return true;
        }

        return super.onMouseClicked(event, isDouble);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // Navigation bar buttons on top
        int iconY = this.posY + 3;

        // Root button
        boolean rootHover = mouseX >= this.posX + 3 && mouseX <= this.posX + 17 && mouseY >= iconY && mouseY <= iconY + 14;
        if (rootHover) {
            RenderUtils.drawOutline(ctx, this.posX + 2, iconY - 1, 16, 16, 0xEEEEEEEE);
        }
        Icons.FILE_ICON_DIR_ROOT.renderAt(ctx, this.posX + 4, iconY + 1, 0, true, false);

        // Up button
        boolean upHover = mouseX >= this.posX + 20 && mouseX <= this.posX + 34 && mouseY >= iconY && mouseY <= iconY + 14;
        if (upHover) {
            RenderUtils.drawOutline(ctx, this.posX + 19, iconY - 1, 16, 16, 0xEEEEEEEE);
        }
        Icons.FILE_ICON_DIR_UP.renderAt(ctx, this.posX + 21, iconY + 1, 0, !this.currentPath.isEmpty(), false);

        // Current path bar
        int pathX = this.posX + 38;
        int searchX = this.widgetSearchBar != null ? this.widgetSearchBar.getX() : (this.posX + this.totalWidth - 100);
        int pathW = searchX - pathX - 4;
        if (pathW > 20) {
            RenderUtils.drawRect(ctx, pathX, iconY, pathW, 14, 0x20FFFFFF);
            RenderUtils.drawOutline(ctx, pathX, iconY, pathW, 14, 0x40FFFFFF);
            String displayPath = "/" + this.currentPath + (this.currentPath.isEmpty() ? "" : "/");
            int maxChars = pathW / 6;
            if (displayPath.length() > maxChars && maxChars > 5) {
                displayPath = "..." + displayPath.substring(displayPath.length() - maxChars + 3);
            }
            this.drawString(ctx, displayPath, pathX + 4, iconY + 3, 0xFFDDDDDD);
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        // Empty folder / no schematics message
        if (this.listContents.isEmpty()) {
            int cy = this.posY + this.totalHeight / 2 - 20;
            if (ClientSchematicManager.getInstance().getServerSchematics().isEmpty()) {
                String dir = ClientSchematicManager.getInstance().getLastServerDirectory();
                drawCentered(ctx, cy, 0xFFFF5555, StringUtils.translate("schematic_synchronizer.gui.browser.empty_server"));
                if (dir != null && !dir.isEmpty()) {
                    drawCentered(ctx, cy + 14, 0xFFAAAAAA, StringUtils.translate("schematic_synchronizer.gui.browser.empty_server_hint"));
                    drawCentered(ctx, cy + 28, 0xFFFFFF55, dir);
                }
            } else {
                drawCentered(ctx, cy, 0xFFAAAAAA, StringUtils.translate("schematic_synchronizer.gui.browser.empty_folder"));
            }
        }

        // Hover tooltips for navigation icons
        if (rootHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.root")));
        } else if (upHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.up")));
        }
    }

    private void drawCentered(GuiContext ctx, int y, int color, String text) {
        int w = this.getStringWidth(text);
        int cx = this.posX + (this.totalWidth - w) / 2;
        this.drawStringWithShadow(ctx, text, cx, y, color);
    }
}
