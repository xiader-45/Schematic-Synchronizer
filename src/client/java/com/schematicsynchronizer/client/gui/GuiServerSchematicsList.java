package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.Schema;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class GuiServerSchematicsList extends GuiListBase<ServerBrowserEntry, WidgetServerBrowserEntry, WidgetListServerBrowser> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long lastAutoRefreshTime = 0;

    public GuiServerSchematicsList(Screen parent) {
        super(10, 26);
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_synchronizer.gui.title.server_schematics");
    }

    @Override
    public void initGui() {
        super.initGui();

        // Checkbox "Создать размещение" exactly like in Litematica's native GuiSchematicLoad
        int yCheckbox = this.height - 44;
        String cbLabel = StringUtils.translate("litematica.gui.label.schematic_load.checkbox.create_placement");
        String cbHover = StringUtils.translate("litematica.gui.label.schematic_load.hoverinfo.create_placement");
        WidgetCheckBox checkBox = new WidgetCheckBox(10, yCheckbox, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, cbLabel, cbHover);
        checkBox.setChecked(DataManager.getCreatePlacementOnLoad(), false);
        checkBox.setListener(cb -> {
            DataManager.setCreatePlacementOnLoad(cb.isChecked());
            reCreateButtons();
        });
        this.addWidget(checkBox);

        this.lastAutoRefreshTime = System.currentTimeMillis();
        this.reCreateButtons();

        // Automatically scan and request fresh catalog from server on each GUI open
        ClientSchematicManager.getInstance().requestRefresh();
        ClientHologramGroupManager.getInstance().requestGroups();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    private void createButtons() {
        int y = this.height - 24;
        int x = 10;

        ServerBrowserEntry selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        boolean isSchem = (selected != null && selected.isSchematic());

        // Button 1: "Создать голограмму" / "Загрузить схему"
        String loadLabel = DataManager.getCreatePlacementOnLoad()
                ? StringUtils.translate("litematica.gui.button.create_placement")
                : StringUtils.translate("litematica.gui.button.load_schematic_to_memory");
        int loadW = this.getStringWidth(loadLabel) + 18;
        ButtonGeneric btnLoad = new ButtonGeneric(x, y, loadW, 20, loadLabel);
        btnLoad.setEnabled(isSchem);
        addButton(btnLoad, (btn, mouse) -> {
            if (selected != null && selected.isSchematic()) {
                if (DataManager.getCreatePlacementOnLoad()) {
                    ClientSchematicManager.getInstance().loadAndPlaceAtPlayer(selected.getSchematicInfo());
                } else {
                    ClientSchematicManager.getInstance().loadSchematicInMemory(selected.getSchematicInfo());
                }
            }
        });
        x += loadW + 4;

        // Button 2: "Список групп"
        int totalGroups = ClientHologramGroupManager.getInstance().getGroups().size();
        String groupLabel = StringUtils.translate("schematic_synchronizer.gui.button.groups_list_count", totalGroups);
        int grpW = this.getStringWidth(groupLabel) + 16;
        ButtonGeneric btnGroups = new ButtonGeneric(x, y, grpW, 20, groupLabel);
        addButton(btnGroups, (btn, mouse) -> {
            GuiBase.openGui(new GuiHologramGroups(this));
        });
        x += grpW + 4;

        // Button 3: "Список материалов" (litematica.gui.button.material_list)
        String matLabel = StringUtils.translate("litematica.gui.button.material_list");
        int matW = this.getStringWidth(matLabel) + 16;
        ButtonGeneric btnMat = new ButtonGeneric(x, y, matW, 20, matLabel);
        btnMat.setEnabled(isSchem);
        addButton(btnMat, (btn, mouse) -> {
            if (selected != null && selected.isSchematic()) {
                ClientSchematicManager.getInstance().openMaterialList(selected.getSchematicInfo(), this);
            }
        });
        x += matW + 4;

        // Button 4: "Главное меню" (litematica.gui.button.change_menu.to_main_menu, Right aligned)
        String mmLabel = StringUtils.translate("litematica.gui.button.change_menu.to_main_menu");
        int mmW = this.getStringWidth(mmLabel) + 20;
        int mmX = this.width - mmW - 10;
        ButtonGeneric btnMM = new ButtonGeneric(mmX, y, mmW, 20, mmLabel);
        addButton(btnMM, (btn, mouse) -> closeGui(true));
    }

    public void onSelectionChange(ServerBrowserEntry entry) {
        this.reCreateButtons();
    }

    public void onSchematicDoubleClicked(ServerBrowserEntry entry) {
        if (entry != null && entry.isSchematic()) {
            if (DataManager.getCreatePlacementOnLoad()) {
                ClientSchematicManager.getInstance().loadAndPlaceAtPlayer(entry.getSchematicInfo());
            } else {
                ClientSchematicManager.getInstance().loadSchematicInMemory(entry.getSchematicInfo());
            }
        }
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        if (now - this.lastAutoRefreshTime >= 1000L) {
            this.lastAutoRefreshTime = now;
            ClientSchematicManager.getInstance().requestRefresh();
            ClientHologramGroupManager.getInstance().requestGroups();
        }
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawSelectedEntryInfo(ctx, mouseX, mouseY);
    }

    private void drawSelectedEntryInfo(GuiContext ctx, int mouseX, int mouseY) {
        int boxX = 10 + getBrowserWidth() + 4;
        int boxY = 26;
        int boxW = getInfoWidth();
        int boxH = getBrowserHeight();

        RenderUtils.drawOutlinedBox(ctx, boxX, boxY, boxW, boxH, 0xA0000000, 0xFF999999);

        ServerBrowserEntry selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        int curY = boxY + 6;
        int contentX = boxX + 6;
        int maxTextW = boxW - 12;

        if (selected == null) {
            String title = StringUtils.translate("schematic_synchronizer.gui.info.title");
            this.drawStringWithShadow(ctx, "§6§l" + title, contentX, curY, 0xFFFFAA00);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.select_schematic_line1"), contentX, curY, 0xFFAAAAAA);
            curY += 10;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.select_schematic_line2"), contentX, curY, 0xFFAAAAAA);
            curY += 16;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            String dir = ClientSchematicManager.getInstance().getLastServerDirectory();
            if (dir != null && !dir.isEmpty()) {
                this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.server_folder"), contentX, curY, 0xFFAAAAAA);
                curY += 10;
                List<String> wrapped = wrapString(dir, maxTextW);
                for (String line : wrapped) {
                    this.drawString(ctx, "§e" + line, contentX, curY, 0xFFFFFF55);
                    curY += 10;
                }
                curY += 4;
            }

            int count = ClientSchematicManager.getInstance().getServerSchematics().size();
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.total_schematics", count), contentX, curY, 0xFFFFFFFF);
            return;
        }

        if (selected.isUp()) {
            this.drawStringWithShadow(ctx, "§7§l" + StringUtils.translate("schematic_synchronizer.gui.info.up_title"), contentX, curY, 0xFFAAAAAA);
            curY += 14;
            this.drawString(ctx, "§a" + StringUtils.translate("schematic_synchronizer.gui.info.up_hint1"), contentX, curY, 0xFFAAAAAA);
            curY += 10;
            this.drawString(ctx, "§a" + StringUtils.translate("schematic_synchronizer.gui.info.up_hint2"), contentX, curY, 0xFFAAAAAA);
            return;
        }

        if (selected.isDirectory()) {
            this.drawStringWithShadow(ctx, "§e§l📁 " + selected.getName() + "/", contentX, curY, 0xFFFFFF55);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.dir_path", selected.getFullPath()), contentX, curY, 0xFFAAAAAA);
            curY += 12;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.info.dir_schematics", selected.getFileCount()), contentX, curY, 0xFFFFFFFF);
            curY += 16;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;
            this.drawString(ctx, "§a" + StringUtils.translate("schematic_synchronizer.gui.info.dir_hint1"), contentX, curY, 0xFF55FF55);
            curY += 10;
            this.drawString(ctx, "§a" + StringUtils.translate("schematic_synchronizer.gui.info.dir_hint2"), contentX, curY, 0xFF55FF55);
            return;
        }

        if (selected.isSchematic()) {
            ServerSchematicInfo info = selected.getSchematicInfo();
            int colorLabel = 0xC0C0C0C0;
            int colorValue = 0xFFFFFFFF;

            // 1. Name
            String nameLabel = StringUtils.translate("litematica.gui.label.schematic_info.name");
            this.drawString(ctx, nameLabel, contentX, curY, colorLabel);
            curY += 11;
            this.drawString(ctx, selected.getName(), contentX + 4, curY, colorValue);
            curY += 11;

            if (info != null) {
                // 2. Author (if present)
                if (info.getAuthor() != null && !info.getAuthor().isEmpty() && !info.getAuthor().equals("?")) {
                    String authorStr = StringUtils.translate("litematica.gui.label.schematic_info.schematic_author", info.getAuthor());
                    this.drawString(ctx, authorStr, contentX, curY, colorLabel);
                    curY += 11;
                }

                // 3. Time Created
                if (info.getTimeCreated() > 0) {
                    String dateStr = DATE_FORMAT.format(new Date(info.getTimeCreated()));
                    String createdStr = StringUtils.translate("litematica.gui.label.schematic_info.time_created", dateStr);
                    this.drawString(ctx, createdStr, contentX, curY, colorLabel);
                    curY += 11;
                }

                // 4. Region Count
                String regStr = StringUtils.translate("litematica.gui.label.schematic_info.region_count", info.getRegionCount());
                this.drawString(ctx, regStr, contentX, curY, colorLabel);
                curY += 11;

                // 5. Total Volume
                if (info.getTotalVolume() > 0) {
                    String volStr = StringUtils.translate("litematica.gui.label.schematic_info.total_volume", info.getTotalVolume());
                    this.drawString(ctx, volStr, contentX, curY, colorLabel);
                    curY += 11;
                }

                // 6. Total Blocks
                if (info.getTotalBlocks() > 0) {
                    String blocksStr = StringUtils.translate("litematica.gui.label.schematic_info.total_blocks", info.getTotalBlocks());
                    this.drawString(ctx, blocksStr, contentX, curY, colorLabel);
                    curY += 11;
                }

                // 7. Enclosing Size
                if (info.getSizeX() > 0 && info.getSizeY() > 0 && info.getSizeZ() > 0) {
                    String encLabel = StringUtils.translate("litematica.gui.label.schematic_info.enclosing_size");
                    this.drawString(ctx, encLabel, contentX, curY, colorLabel);
                    curY += 11;
                    String encSizeStr = String.format("%d x %d x %d", info.getSizeX(), info.getSizeY(), info.getSizeZ());
                    this.drawString(ctx, encSizeStr, contentX + 4, curY, colorValue);
                    curY += 11;
                }

                // 8. Minecraft Version / Schema
                if (info.getMinecraftDataVersion() > 0) {
                    Schema schema = Schema.getSchemaByDataVersion(info.getMinecraftDataVersion());
                    String mcVer = (schema != null) ? schema.getString() : ("DataVersion " + info.getMinecraftDataVersion());
                    String schemaStr = StringUtils.translate("litematica.gui.label.schematic_info.schema", mcVer, info.getMinecraftDataVersion());
                    this.drawString(ctx, schemaStr, contentX, curY, colorLabel);
                    curY += 11;
                }

                // 9. File Size & Cache status
                String statusCached = StringUtils.translate("schematic_synchronizer.gui.info.status_cached");
                String statusServer = StringUtils.translate("schematic_synchronizer.gui.info.status_server");
                String cacheStatus = selected.isCached() ? ("§a✔ " + statusCached) : ("§e☁ " + statusServer);
                String fileStr = StringUtils.translate("schematic_synchronizer.gui.info.file_label", "§f" + info.getFormattedSize(), "§7" + cacheStatus + "§7");
                this.drawString(ctx, fileStr, contentX, curY, colorLabel);
                curY += 13;
            }

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            List<HologramGroupData> groups = ClientHologramGroupManager.getInstance().getGroupsForSchematic(info);
            String groupHdr = StringUtils.translate("schematic_synchronizer.gui.info.groups_header", groups.size());
            this.drawStringWithShadow(ctx, "§6" + groupHdr, contentX, curY, 0xFFFFAA00);
            curY += 12;

            if (groups.isEmpty()) {
                this.drawString(ctx, "  §7" + StringUtils.translate("schematic_synchronizer.gui.info.no_groups"), contentX, curY, 0xFFAAAAAA);
            } else {
                for (int i = 0; i < groups.size(); i++) {
                    if (curY + 22 > boxY + boxH) {
                        String moreStr = StringUtils.translate("schematic_synchronizer.gui.info.more_groups", groups.size() - i);
                        this.drawString(ctx, "  §7" + moreStr, contentX, curY, 0xFF888888);
                        break;
                    }
                    HologramGroupData g = groups.get(i);
                    String prefix = "§7• §e";
                    this.drawString(ctx, prefix + g.getName(), contentX, curY, 0xFFFFAA00);
                    curY += 10;
                    String dim = g.getDimension();
                    if (dim.contains(":")) dim = dim.substring(dim.indexOf(':') + 1);
                    this.drawString(ctx, "   §7" + dim + " (" + StringUtils.translate("schematic_synchronizer.gui.group.placements_count", g.getPlacements().size()) + ")", contentX, curY, 0xFFAAAAAA);
                    curY += 12;
                }
            }
        }
    }

    private List<String> wrapString(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            current.append(text.charAt(i));
            if (this.getStringWidth(current.toString()) >= maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            closeGui(true);
            return true;
        }

        if (event.key() == 257 || event.key() == 335) { // GLFW_KEY_ENTER or NUMPAD_ENTER
            ServerBrowserEntry selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
            if (selected != null) {
                if (selected.isUp()) {
                    getListWidget().goUp();
                    return true;
                } else if (selected.isDirectory()) {
                    getListWidget().enterDirectory(selected.getFullPath());
                    return true;
                } else if (selected.isSchematic()) {
                    onSchematicDoubleClicked(selected);
                    return true;
                }
            }
        }

        return super.onKeyTyped(event);
    }

    public void refreshList() {
        if (getListWidget() != null) {
            getListWidget().refreshEntries();
            this.reCreateButtons();
        }
    }

    @Override
    protected WidgetListServerBrowser createListWidget(int listX, int listY) {
        return new WidgetListServerBrowser(listX, listY, getBrowserWidth(), getBrowserHeight(), this, this::onSelectionChange);
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
