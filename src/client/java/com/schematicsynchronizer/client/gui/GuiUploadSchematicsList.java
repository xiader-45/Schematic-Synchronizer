package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.server.ServerSchematicManager;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.Schema;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

public class GuiUploadSchematicsList extends GuiListBase<LocalFileEntry, WidgetUploadBrowserEntry, WidgetListUploadBrowser> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final String targetServerFolder;

    public GuiUploadSchematicsList(Screen parent, String targetServerFolder) {
        super(10, 26);
        this.setParent(parent);
        this.targetServerFolder = targetServerFolder != null ? targetServerFolder : "";
        this.title = StringUtils.translate("schematic_synchronizer.gui.title.upload_schematics");
    }

    @Override
    public void initGui() {
        super.initGui();
        reCreateButtons();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    private void createButtons() {
        int y = this.height - 24;
        int x = 10;

        Set<LocalFileEntry> selected = getListWidget() != null ? getListWidget().getMultiSelected() : Collections.emptySet();
        boolean hasSelection = !selected.isEmpty();

        // Button 1: "Загрузить на сервер"
        String uploadLabel = selected.size() > 1
                ? StringUtils.translate("schematic_synchronizer.gui.button.upload_to_server_count", selected.size())
                : StringUtils.translate("schematic_synchronizer.gui.button.upload_to_server");
        int uploadW = this.getStringWidth(uploadLabel) + 20;
        ButtonGeneric btnUpload = new ButtonGeneric(x, y, uploadW, 20, uploadLabel);
        btnUpload.setEnabled(hasSelection);
        addButton(btnUpload, (btn, mouse) -> {
            performUpload(selected);
            closeGui(true);
        });
        x += uploadW + 6;

        // Button 2: "Назад"
        String backLabel = StringUtils.translate("gui.back");
        int backW = this.getStringWidth(backLabel) + 20;
        int backX = this.width - backW - 10;
        ButtonGeneric btnBack = new ButtonGeneric(backX, y, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    private void performUpload(Set<LocalFileEntry> selectedEntries) {
        List<Path> filesToUpload = new ArrayList<>();
        Map<Path, String> fileToTargetPath = new HashMap<>();

        for (LocalFileEntry entry : selectedEntries) {
            if (entry.isSchematic() && Files.exists(entry.getPath())) {
                filesToUpload.add(entry.getPath());
                String target = buildTargetPath(entry.getName());
                fileToTargetPath.put(entry.getPath(), target);
            } else if (entry.isDirectory() && Files.exists(entry.getPath())) {
                collectFilesFromDirectory(entry.getPath(), entry.getName(), filesToUpload, fileToTargetPath);
            }
        }

        for (Path file : filesToUpload) {
            String targetId = fileToTargetPath.get(file);
            if (targetId != null) {
                ClientSchematicManager.getInstance().uploadFile(file, targetId);
            }
        }

        ClientSchematicManager.getInstance().requestRefresh();
    }

    private void collectFilesFromDirectory(Path dir, String dirName, List<Path> files, Map<Path, String> fileToTargetPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && !p.getFileName().toString().startsWith(".")) {
                    collectFilesFromDirectory(p, dirName + "/" + p.getFileName().toString(), files, fileToTargetPath);
                } else if (Files.isRegularFile(p)) {
                    String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".litematic") || lower.endsWith(".schematic") ||
                        lower.endsWith(".schem") || lower.endsWith(".nbt")) {
                        files.add(p);
                        String target = buildTargetPath(dirName + "/" + p.getFileName().toString());
                        fileToTargetPath.put(p, target);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String buildTargetPath(String relativeName) {
        if (this.targetServerFolder == null || this.targetServerFolder.isEmpty()) {
            return relativeName;
        }
        return this.targetServerFolder + "/" + relativeName;
    }

    public void onSelectionChanged() {
        reCreateButtons();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawSelectedEntryInfo(ctx, mouseX, mouseY);
    }

    private void drawSelectedEntryInfo(GuiContext ctx, int mouseX, int mouseY) {
        int boxX = 10 + getBrowserWidth() + 4;
        int boxY = 26;
        int boxW = getInfoWidth();
        int boxH = getBrowserHeight();

        RenderUtils.drawOutlinedBox(ctx, boxX, boxY, boxW, boxH, 0xA0000000, 0xFF999999);

        Set<LocalFileEntry> selected = getListWidget() != null ? getListWidget().getMultiSelected() : Collections.emptySet();
        int curY = boxY + 6;
        int contentX = boxX + 6;
        int maxTextW = boxW - 12;

        if (selected.isEmpty()) {
            String title = StringUtils.translate("schematic_synchronizer.gui.upload_info.title");
            this.drawStringWithShadow(ctx, "§6§l" + title, contentX, curY, 0xFFFFAA00);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.upload_info.select_hint"), contentX, curY, 0xFFAAAAAA);
            curY += 18;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            String srvDir = this.targetServerFolder.isEmpty() ? "/" : ("/" + this.targetServerFolder);
            String targetStr = StringUtils.translate("schematic_synchronizer.gui.upload_info.target_server_folder", srvDir);
            this.drawString(ctx, "§7" + targetStr, contentX, curY, 0xFFAAAAAA);
            return;
        }

        if (selected.size() > 1) {
            String title = StringUtils.translate("schematic_synchronizer.gui.upload_info.multiple_selected", selected.size());
            this.drawStringWithShadow(ctx, "§a§l" + title, contentX, curY, 0xFF55FF55);
            curY += 14;

            long totalSize = 0;
            for (LocalFileEntry e : selected) {
                totalSize += e.getSize();
            }
            String totalSizeStr = "§7" + formatSize(totalSize);
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.file_label", totalSizeStr, ""), contentX, curY, 0xFFAAAAAA);
            curY += 14;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            for (LocalFileEntry e : selected) {
                if (curY + 12 > boxY + boxH) {
                    this.drawString(ctx, "§7...", contentX, curY, 0xFF888888);
                    break;
                }
                String icon = e.isDirectory() ? "📁 " : "📄 ";
                this.drawString(ctx, "§f" + icon + e.getName(), contentX, curY, 0xFFFFFFFF);
                curY += 11;
            }
            return;
        }

        LocalFileEntry entry = selected.iterator().next();
        if (entry.isDirectory()) {
            this.drawStringWithShadow(ctx, "§e§l📁 " + entry.getName() + "/", contentX, curY, 0xFFFFFF55);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.upload_info.directory_schematics", entry.getFileCount()), contentX, curY, 0xFFFFFFFF);
            curY += 16;
            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;
            String srvDir = this.targetServerFolder.isEmpty() ? "/" : ("/" + this.targetServerFolder);
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.upload_info.target_server_folder", srvDir), contentX, curY, 0xFFAAAAAA);
            return;
        }

        if (entry.isSchematic()) {
            ServerSchematicManager.SchematicMetadataDetails meta = entry.getMetadata();
            int colorLabel = 0xC0C0C0C0;
            int colorValue = 0xFFFFFFFF;

            // Name
            this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.name"), contentX, curY, colorLabel);
            curY += 11;
            this.drawString(ctx, entry.getName(), contentX + 4, curY, colorValue);
            curY += 11;

            if (meta != null) {
                if (meta.author != null && !meta.author.isEmpty() && !meta.author.equals("?")) {
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.schematic_author", meta.author), contentX, curY, colorLabel);
                    curY += 11;
                }
                if (meta.timeCreated > 0) {
                    String dateStr = DATE_FORMAT.format(new Date(meta.timeCreated));
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.time_created", dateStr), contentX, curY, colorLabel);
                    curY += 11;
                }
                this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.region_count", meta.regionCount), contentX, curY, colorLabel);
                curY += 11;
                if (meta.totalVolume > 0) {
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.total_volume", meta.totalVolume), contentX, curY, colorLabel);
                    curY += 11;
                }
                if (meta.totalBlocks > 0) {
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.total_blocks", meta.totalBlocks), contentX, curY, colorLabel);
                    curY += 11;
                }
                if (meta.sizeX > 0 && meta.sizeY > 0 && meta.sizeZ > 0) {
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.enclosing_size"), contentX, curY, colorLabel);
                    curY += 11;
                    this.drawString(ctx, String.format("%d x %d x %d", meta.sizeX, meta.sizeY, meta.sizeZ), contentX + 4, curY, colorValue);
                    curY += 11;
                }
                if (meta.minecraftDataVersion > 0) {
                    Schema schema = Schema.getSchemaByDataVersion(meta.minecraftDataVersion);
                    String mcVer = (schema != null) ? schema.getString() : ("DataVersion " + meta.minecraftDataVersion);
                    this.drawString(ctx, StringUtils.translate("litematica.gui.label.schematic_info.schema", mcVer, meta.minecraftDataVersion), contentX, curY, colorLabel);
                    curY += 11;
                }
            }

            this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.info.file_label", "§f" + entry.getFormattedSize(), ""), contentX, curY, colorLabel);
            curY += 13;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            String srvDir = this.targetServerFolder.isEmpty() ? "/" : ("/" + this.targetServerFolder);
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.upload_info.target_server_folder", srvDir), contentX, curY, 0xFFAAAAAA);
        }
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", size / (1024.0 * 1024.0));
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == 256) { // ESCAPE
            closeGui(true);
            return true;
        }
        return super.onKeyTyped(event);
    }

    @Override
    protected WidgetListUploadBrowser createListWidget(int listX, int listY) {
        return new WidgetListUploadBrowser(listX, listY, getBrowserWidth(), getBrowserHeight(), this);
    }

    protected int getInfoWidth() {
        return Math.max(170, Math.min(230, (int) ((this.width - 24) * 0.33)));
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 24 - getInfoWidth() - 4;
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 74;
    }
}
