package com.schematicsynchronizer.client.gui;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WidgetListUploadBrowser extends WidgetListBase<LocalFileEntry, WidgetUploadBrowserEntry> {
    private final GuiUploadSchematicsList parent;
    private final Path rootDirectory;
    private Path currentDirectory;
    private final Set<LocalFileEntry> multiSelected = new LinkedHashSet<>();
    private final List<LocalFileEntry> rawEntries = new ArrayList<>();

    public WidgetListUploadBrowser(int x, int y, int width, int height, GuiUploadSchematicsList parent) {
        super(x, y, width, height, null);
        this.parent = parent;
        this.rootDirectory = FabricLoader.getInstance().getGameDir().resolve("schematics");
        this.currentDirectory = this.rootDirectory;
        this.entryHeight = 20;
        this.browserEntriesOffsetY = 24;

        int searchW = Math.min(130, Math.max(70, width / 4));
        int searchX = x + width - searchW - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 3, searchW, 15, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);

        try {
            if (!Files.exists(this.rootDirectory)) {
                Files.createDirectories(this.rootDirectory);
            }
        } catch (IOException ignored) {
        }

        refreshEntries();
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Set<LocalFileEntry> getMultiSelected() {
        return Collections.unmodifiableSet(this.multiSelected);
    }

    public boolean isEntrySelected(LocalFileEntry entry) {
        return this.multiSelected.contains(entry);
    }

    public void handleEntrySelection(LocalFileEntry entry, boolean isMulti) {
        if (entry.isUp()) {
            this.multiSelected.clear();
            this.parent.onSelectionChanged();
            return;
        }

        if (isMulti) {
            if (this.multiSelected.contains(entry)) {
                this.multiSelected.remove(entry);
            } else {
                this.multiSelected.add(entry);
            }
        } else {
            this.multiSelected.clear();
            this.multiSelected.add(entry);
        }

        this.parent.onSelectionChanged();
    }

    public void clearSelection() {
        this.multiSelected.clear();
        this.parent.onSelectionChanged();
    }

    public void goToRoot() {
        this.currentDirectory = this.rootDirectory;
        this.clearSelection();
        refreshEntries();
    }

    public void goUp() {
        if (!this.currentDirectory.equals(this.rootDirectory) && this.currentDirectory.getParent() != null) {
            this.currentDirectory = this.currentDirectory.getParent();
            this.clearSelection();
            refreshEntries();
        }
    }

    public void enterDirectory(Path dir) {
        if (dir != null && Files.isDirectory(dir)) {
            this.currentDirectory = dir;
            this.clearSelection();
            refreshEntries();
        }
    }

    @Override
    public void refreshEntries() {
        this.rawEntries.clear();

        if (!this.currentDirectory.equals(this.rootDirectory)) {
            this.rawEntries.add(LocalFileEntry.up(this.currentDirectory.getParent()));
        }

        List<LocalFileEntry> dirs = new ArrayList<>();
        List<LocalFileEntry> files = new ArrayList<>();

        if (Files.exists(this.currentDirectory) && Files.isDirectory(this.currentDirectory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.currentDirectory)) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    // Skip internal / hidden folders
                    if (name.startsWith(".") || name.equalsIgnoreCase(".server_schematics") || name.equalsIgnoreCase(".server_cache") || name.equalsIgnoreCase("server")) {
                        continue;
                    }

                    if (Files.isDirectory(p)) {
                        int count = countSchematics(p);
                        dirs.add(LocalFileEntry.directory(p, count));
                    } else if (Files.isRegularFile(p)) {
                        String lower = name.toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".litematic") || lower.endsWith(".schematic") ||
                            lower.endsWith(".schem") || lower.endsWith(".nbt")) {
                            files.add(LocalFileEntry.schematic(p));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        dirs.sort(Comparator.comparing(LocalFileEntry::getName, String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(LocalFileEntry::getName, String.CASE_INSENSITIVE_ORDER));

        this.rawEntries.addAll(dirs);
        this.rawEntries.addAll(files);

        reapplyFilter();
    }

    private int countSchematics(Path dir) {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p) && (name.endsWith(".litematic") || name.endsWith(".schematic") || name.endsWith(".schem") || name.endsWith(".nbt"))) {
                    count++;
                } else if (Files.isDirectory(p) && !name.startsWith(".")) {
                    count += countSchematics(p);
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }

    public void reapplyFilter() {
        this.listContents.clear();
        String search = (this.widgetSearchBar != null) ? this.widgetSearchBar.getFilter().toLowerCase(Locale.ROOT).trim() : "";

        for (LocalFileEntry entry : this.rawEntries) {
            if (entry.isUp()) {
                if (search.isEmpty()) {
                    this.listContents.add(entry);
                }
                continue;
            }

            if (search.isEmpty() || entry.getName().toLowerCase(Locale.ROOT).contains(search)) {
                this.listContents.add(entry);
            }
        }

        reCreateListEntryWidgets();
    }

    @Override
    protected WidgetUploadBrowserEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, LocalFileEntry entry) {
        return new WidgetUploadBrowserEntry(x, y, this.browserEntryWidth, this.entryHeight, entry, listIndex, this);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        if (event.input() == 0 && my >= this.posY + 2 && my <= this.posY + 20) {
            // Root
            if (mx >= this.posX + 3 && mx <= this.posX + 17) {
                goToRoot();
                return true;
            }
            // Up
            if (mx >= this.posX + 19 && mx <= this.posX + 33) {
                goUp();
                return true;
            }
            // Open directory
            if (mx >= this.posX + 35 && mx <= this.posX + 49) {
                try {
                    Util.getPlatform().openPath(this.currentDirectory);
                } catch (Throwable ignored) {
                }
                return true;
            }
        }

        return super.onMouseClicked(event, isDouble);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int barY = this.posY + 2;
        int barH = 18;

        // Top bar buttons
        boolean isRoot = this.currentDirectory.equals(this.rootDirectory);
        int rootX = this.posX + 3;
        int upX = this.posX + 19;
        int openX = this.posX + 35;
        int pathX = this.posX + 53;

        int searchW = Math.min(130, Math.max(70, this.totalWidth / 4));
        int searchX = this.posX + this.totalWidth - searchW - 4;
        int pathW = searchX - pathX - 4;

        if (this.widgetSearchBar != null) {
            this.widgetSearchBar.setPosition(searchX, barY + 1);
        }

        // Icons
        Icons.FILE_ICON_DIR_ROOT.renderAt(ctx, rootX, barY + 2, 0, false, !isRoot);
        Icons.FILE_ICON_DIR_UP.renderAt(ctx, upX, barY + 2, 0, false, !isRoot);
        Icons.FILE_ICON_DIR.renderAt(ctx, openX, barY + 2, 0, false, true);

        // Hover boxes
        if (mouseY >= barY && mouseY <= barY + barH) {
            if (mouseX >= rootX - 1 && mouseX <= rootX + 15) {
                RenderUtils.drawOutline(ctx, rootX - 1, barY + 1, 16, 16, 0xEEEEEEEE);
            } else if (mouseX >= upX - 1 && mouseX <= upX + 15) {
                RenderUtils.drawOutline(ctx, upX - 1, barY + 1, 16, 16, 0xEEEEEEEE);
            } else if (mouseX >= openX - 1 && mouseX <= openX + 15) {
                RenderUtils.drawOutline(ctx, openX - 1, barY + 1, 16, 16, 0xEEEEEEEE);
            }
        }

        // Path box
        RenderUtils.drawRect(ctx, pathX, barY, pathW, barH, 0x50000000);
        RenderUtils.drawOutline(ctx, pathX, barY, pathW, barH, 0x80FFFFFF);

        String relPath = "/";
        try {
            if (!this.currentDirectory.equals(this.rootDirectory)) {
                relPath = "/" + this.rootDirectory.relativize(this.currentDirectory).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        this.drawString(ctx, "§e" + relPath, pathX + 4, barY + 5, 0xFFFFFF55);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        // Render hover tooltips
        if (mouseY >= barY && mouseY <= barY + barH) {
            if (mouseX >= rootX - 1 && mouseX <= rootX + 15) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.root")));
            } else if (mouseX >= upX - 1 && mouseX <= upX + 15) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.up")));
            } else if (mouseX >= openX - 1 && mouseX <= openX + 15) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(StringUtils.translate("malilib.gui.button.hover.directory_widget.open_directory")));
            }
        }
    }
}
