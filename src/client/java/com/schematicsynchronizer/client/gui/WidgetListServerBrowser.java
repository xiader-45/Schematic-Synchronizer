package com.schematicsynchronizer.client.gui;

import com.google.common.collect.ImmutableList;
import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInputFeedback;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WidgetListServerBrowser extends WidgetListBase<ServerBrowserEntry, WidgetServerBrowserEntry> {
    private static final Identifier ICON_UPLOAD = SchematicSynchronizer.id("textures/gui/upload.png");

    private final GuiServerSchematicsList parent;
    private String currentPath = "";

    public WidgetListServerBrowser(int x, int y, int width, int height,
                                  GuiServerSchematicsList parent,
                                  ISelectionListener<ServerBrowserEntry> selectionListener) {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.browserEntryHeight = 22;

        int searchWidth = Math.min(130, Math.max(70, width / 4));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 3, searchWidth, 15, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
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
    public void refreshEntries() {
        ServerBrowserEntry prevSelected = this.getLastSelectedEntry();
        super.refreshEntries();
        if (prevSelected != null) {
            boolean found = false;
            int idx = 0;
            for (ServerBrowserEntry entry : this.listContents) {
                if (entry.equals(prevSelected)) {
                    this.setLastSelectedEntry(entry, idx);
                    found = true;
                    break;
                }
                idx++;
            }
            if (!found) {
                this.clearSelection();
                this.parent.onSelectionChange(null);
            }
        }
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

        // Register all server directories (including empty ones)
        Collection<String> allServerDirs = ClientSchematicManager.getInstance().getServerDirectories();
        for (String d : allServerDirs) {
            String dirPath = d.replace('\\', '/');
            while (dirPath.startsWith("/")) {
                dirPath = dirPath.substring(1);
            }
            while (dirPath.endsWith("/")) {
                dirPath = dirPath.substring(0, dirPath.length() - 1);
            }
            if (dirPath.isEmpty()) continue;

            if (cur.isEmpty()) {
                int slashIdx = dirPath.indexOf('/');
                String sub = (slashIdx < 0) ? dirPath : dirPath.substring(0, slashIdx);
                subDirs.putIfAbsent(sub, 0);
            } else {
                if (dirPath.startsWith(cur)) {
                    String rem = dirPath.substring(cur.length());
                    int slashIdx = rem.indexOf('/');
                    String sub = (slashIdx < 0) ? rem : rem.substring(0, slashIdx);
                    if (!sub.isEmpty()) {
                        subDirs.putIfAbsent(sub, 0);
                    }
                }
            }
        }

        // Count schematics in subdirectories and collect current directory files
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
        if (event.input() != 0) {
            return super.onMouseClicked(event, isDouble);
        }

        int mx = (int) event.x();
        int my = (int) event.y();
        int iconY = this.posY + 3;

        if (my >= iconY && my <= iconY + 16) {
            // Root (posX + 3 .. 17)
            if (mx >= this.posX + 3 && mx <= this.posX + 17) {
                goToRoot();
                return true;
            }
            // Up (posX + 19 .. 33)
            if (mx >= this.posX + 19 && mx <= this.posX + 33) {
                goUp();
                return true;
            }
            // Create Dir (posX + 35 .. 49)
            if (mx >= this.posX + 35 && mx <= this.posX + 49) {
                GuiTextInputFeedback dialog = new GuiTextInputFeedback(
                        256,
                        StringUtils.translate("malilib.gui.title.create_directory"),
                        "",
                        this.parent,
                        name -> {
                            if (name == null || name.trim().isEmpty()) return false;
                            String clean = name.trim();
                            if (clean.contains("/") || clean.contains("\\") || clean.contains("..")) return false;
                            String rel = this.currentPath.isEmpty() ? clean : (this.currentPath + "/" + clean);
                            ClientSchematicManager.getInstance().createServerDirectory(rel);
                            try {
                                Path local = ClientSchematicManager.getInstance().getCacheDirectory().resolve(rel);
                                if (!Files.exists(local)) {
                                    Files.createDirectories(local);
                                }
                            } catch (Exception ignored) {
                            }
                            enterDirectory(rel);
                            return true;
                        }
                );
                GuiBase.openGui(dialog);
                return true;
            }
            // Open Dir (posX + 51 .. 65)
            if (mx >= this.posX + 51 && mx <= this.posX + 65) {
                Path base = ClientSchematicManager.getInstance().getCacheDirectory();
                if (!this.currentPath.isEmpty()) {
                    base = base.resolve(this.currentPath);
                }
                try {
                    if (!Files.exists(base)) {
                        Files.createDirectories(base);
                    }
                    Util.getPlatform().openPath(base);
                } catch (Throwable ignored) {
                }
                return true;
            }
            // Upload (posX + 67 .. 81)
            if (mx >= this.posX + 67 && mx <= this.posX + 81) {
                GuiBase.openGui(new GuiUploadSchematicsList(this.parent, this.currentPath));
                return true;
            }
        }

        return super.onMouseClicked(event, isDouble);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int iconY = this.posY + 3;

        int rootX = this.posX + 3;
        int upX = this.posX + 19;
        int createDirX = this.posX + 35;
        int openDirX = this.posX + 51;
        int uploadX = this.posX + 67;
        int pathX = this.posX + 85;

        int searchX = this.widgetSearchBar != null ? this.widgetSearchBar.getX() : (this.posX + this.totalWidth - 100);
        int pathW = searchX - pathX - 4;

        boolean rootHover = mouseX >= rootX && mouseX <= rootX + 14 && mouseY >= iconY && mouseY <= iconY + 14;
        boolean upHover = mouseX >= upX && mouseX <= upX + 14 && mouseY >= iconY && mouseY <= iconY + 14;
        boolean createHover = mouseX >= createDirX && mouseX <= createDirX + 14 && mouseY >= iconY && mouseY <= iconY + 14;
        boolean openHover = mouseX >= openDirX && mouseX <= openDirX + 14 && mouseY >= iconY && mouseY <= iconY + 14;
        boolean uploadHover = mouseX >= uploadX && mouseX <= uploadX + 14 && mouseY >= iconY && mouseY <= iconY + 14;

        // Hover outlines
        if (rootHover) RenderUtils.drawOutline(ctx, rootX - 1, iconY - 1, 16, 16, 0xEEEEEEEE);
        if (upHover) RenderUtils.drawOutline(ctx, upX - 1, iconY - 1, 16, 16, 0xEEEEEEEE);
        if (createHover) RenderUtils.drawOutline(ctx, createDirX - 1, iconY - 1, 16, 16, 0xEEEEEEEE);
        if (openHover) RenderUtils.drawOutline(ctx, openDirX - 1, iconY - 1, 16, 16, 0xEEEEEEEE);
        if (uploadHover) RenderUtils.drawOutline(ctx, uploadX - 1, iconY - 1, 16, 16, 0xEEEEEEEE);

        // Icons
        Icons.FILE_ICON_DIR_ROOT.renderAt(ctx, rootX + 1, iconY + 1, 0, true, false);
        Icons.FILE_ICON_DIR_UP.renderAt(ctx, upX + 1, iconY + 1, 0, !this.currentPath.isEmpty(), false);
        Icons.FILE_ICON_CREATE_DIR.renderAt(ctx, createDirX + 1, iconY + 1, 0, true, false);
        Icons.FILE_ICON_DIR.renderAt(ctx, openDirX + 1, iconY + 1, 0, true, false);
        ctx.blit(RenderPipelines.GUI_TEXTURED, ICON_UPLOAD, uploadX + 3, iconY + 3, 0.0f, 0.0f, 9, 9, 9, 9);

        // Current path bar
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
        boolean hasContent = false;
        for (ServerBrowserEntry entry : this.listContents) {
            if (!entry.isUp()) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            if (ClientSchematicManager.getInstance().getServerSchematics().isEmpty() && ClientSchematicManager.getInstance().getServerDirectories().isEmpty()) {
                String dir = ClientSchematicManager.getInstance().getLastServerDirectory();
                boolean hasDir = dir != null && !dir.isEmpty();

                String line1 = StringUtils.translate("schematic_synchronizer.gui.browser.empty_server");
                String line2 = StringUtils.translate("schematic_synchronizer.gui.browser.empty_server_upload");
                String folderLabel = StringUtils.translate("schematic_synchronizer.gui.browser.empty_server_folder");

                boolean singleLineDir = hasDir && (this.getStringWidth(folderLabel + " " + dir) <= this.totalWidth - 20);
                int totalLines = hasDir ? (singleLineDir ? 3 : 4) : 2;
                int startY = this.posY + (this.totalHeight - totalLines * 14) / 2;

                drawCentered(ctx, startY, 0xFFDDDDDD, line1);
                drawCentered(ctx, startY + 14, 0xFFAAAAAA, line2);

                if (hasDir) {
                    if (singleLineDir) {
                        drawCentered(ctx, startY + 28, 0xFFAAAAAA, folderLabel + " " + dir);
                    } else {
                        drawCentered(ctx, startY + 28, 0xFFAAAAAA, folderLabel);
                        drawCentered(ctx, startY + 42, 0xFFCCCCCC, dir);
                    }
                }
            } else {
                int cy = this.posY + this.totalHeight / 2 - 10;
                drawCentered(ctx, cy, 0xFFAAAAAA, StringUtils.translate("schematic_synchronizer.gui.browser.empty_folder"));
            }
        }

        // Hover tooltips for navigation icons
        if (rootHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.root")));
        } else if (upHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.up")));
        } else if (createHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.create_directory")));
        } else if (openHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.open_directory")));
        } else if (uploadHover) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("schematic_synchronizer.gui.button.hover.upload_to_server")));
        }
    }

    private void drawCentered(GuiContext ctx, int y, int color, String text) {
        int w = this.getStringWidth(text);
        int cx = this.posX + (this.totalWidth - w) / 2;
        this.drawStringWithShadow(ctx, text, cx, y, color);
    }
}
