package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class GuiServerPlacementsList extends GuiListBase<PlayerPlacementInfo, WidgetServerPlacementEntry, WidgetListServerPlacements> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private String filterSchematicId;

    public GuiServerPlacementsList(Screen parent, String filterSchematicId) {
        super(10, 26);
        this.setParent(parent);
        this.filterSchematicId = filterSchematicId;
        this.title = StringUtils.translate("schematic_synchronizer.gui.title.server_placements");
    }

    public String getFilterSchematicId() {
        return this.filterSchematicId;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reCreateButtons();
        ClientSchematicManager.getInstance().requestRefresh();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    private void createButtons() {
        int y = this.height - 24;
        int x = 10;

        PlayerPlacementInfo selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        boolean hasSelected = (selected != null);

        // Button 1: "Разместить"
        String placeLabel = StringUtils.translate("schematic_synchronizer.gui.button.place_selected");
        int placeW = this.getStringWidth(placeLabel) + 20;
        ButtonGeneric btnPlace = new ButtonGeneric(x, y, placeW, 20, placeLabel);
        btnPlace.setEnabled(hasSelected);
        addButton(btnPlace, (btn, mouse) -> {
            if (selected != null) {
                ClientSchematicManager.getInstance().placeLikePlayer(selected);
            }
        });
        x += placeW + 4;

        // Button 2: "Список материалов"
        ServerSchematicInfo schemInfo = (selected != null) ? ClientSchematicManager.getInstance().getSchematic(selected.getSchematicId()) : null;
        String matLabel = StringUtils.translate("litematica.gui.button.material_list");
        int matW = this.getStringWidth(matLabel) + 16;
        ButtonGeneric btnMat = new ButtonGeneric(x, y, matW, 20, matLabel);
        btnMat.setEnabled(schemInfo != null);
        addButton(btnMat, (btn, mouse) -> {
            if (schemInfo != null) {
                ClientSchematicManager.getInstance().openMaterialList(schemInfo, this);
            }
        });
        x += matW + 4;

        // Button 3: Toggle filter if opened for specific schematic
        if (this.filterSchematicId != null && !this.filterSchematicId.isEmpty()) {
            String filterLabel = "Показать все";
            int filW = this.getStringWidth(filterLabel) + 16;
            ButtonGeneric btnFilter = new ButtonGeneric(x, y, filW, 20, filterLabel);
            addButton(btnFilter, (btn, mouse) -> {
                this.filterSchematicId = null;
                refreshList();
            });
            x += filW + 4;
        }

        // Button 4: "Обновить"
        String refLabel = StringUtils.translate("litematica.gui.button.material_list.refresh_list");
        int refW = this.getStringWidth(refLabel) + 16;
        ButtonGeneric btnRef = new ButtonGeneric(x, y, refW, 20, refLabel);
        addButton(btnRef, (btn, mouse) -> ClientSchematicManager.getInstance().requestRefresh());

        // Button 5: "Назад" (Right aligned)
        String backLabel = StringUtils.translate("gui.back");
        int backW = this.getStringWidth(backLabel) + 20;
        int backX = this.width - backW - 10;
        ButtonGeneric btnBack = new ButtonGeneric(backX, y, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    public void onSelectionChange(PlayerPlacementInfo entry) {
        this.reCreateButtons();
    }

    public void onPlacementDoubleClicked(PlayerPlacementInfo entry) {
        if (entry != null) {
            ClientSchematicManager.getInstance().placeLikePlayer(entry);
        }
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawSelectedPlacementInfo(ctx, mouseX, mouseY);
    }

    private void drawSelectedPlacementInfo(GuiContext ctx, int mouseX, int mouseY) {
        int boxX = 10 + getBrowserWidth() + 4;
        int boxY = 26;
        int boxW = getInfoWidth();
        int boxH = getBrowserHeight();

        RenderUtils.drawOutlinedBox(ctx, boxX, boxY, boxW, boxH, 0xA0000000, 0xFF999999);

        PlayerPlacementInfo selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        int curY = boxY + 6;
        int contentX = boxX + 6;
        int maxTextW = boxW - 12;

        if (selected == null) {
            String title = StringUtils.translate("schematic_synchronizer.gui.placement_info.title");
            this.drawStringWithShadow(ctx, "§6§l" + title, contentX, curY, 0xFFFFAA00);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.placement_info.select_hint"), contentX, curY, 0xFFAAAAAA);
            curY += 18;

            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 8;

            int total = ClientSchematicManager.getInstance().getAllPlacements().size();
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.placement_info.total", total), contentX, curY, 0xFFFFFFFF);
            return;
        }

        int colorLabel = 0xC0C0C0C0;
        int colorValue = 0xFFFFFFFF;

        // Title: Schematic ID/name
        String schemName = selected.getSchematicId();
        this.drawStringWithShadow(ctx, "§6§l" + StringUtils.translate("schematic_synchronizer.gui.placement_info.title"), contentX, curY, 0xFFFFAA00);
        curY += 14;

        // 1. Schematic
        this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.schematic"), contentX, curY, colorLabel);
        curY += 11;
        this.drawString(ctx, "§e" + schemName, contentX + 4, curY, colorValue);
        curY += 13;

        // 2. Player
        this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.player"), contentX, curY, colorLabel);
        curY += 11;
        this.drawString(ctx, "§b👤 " + selected.getOwnerName(), contentX + 4, curY, 0xFF55FFFF);
        curY += 13;

        // 3. Position
        this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.position"), contentX, curY, colorLabel);
        curY += 11;
        this.drawString(ctx, selected.getPos().toShortString(), contentX + 4, curY, colorValue);
        curY += 13;

        // 4. Dimension
        this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.dimension"), contentX, curY, colorLabel);
        curY += 11;
        String dim = selected.getDimension();
        if (dim.contains(":")) dim = dim.substring(dim.indexOf(':') + 1);
        this.drawString(ctx, "§a" + dim, contentX + 4, curY, 0xFF55FF55);
        curY += 13;

        // 5. Rotation & Mirror
        this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.rotation"), contentX, curY, colorLabel);
        curY += 11;
        this.drawString(ctx, selected.getRotation() + " / " + selected.getMirror(), contentX + 4, curY, colorValue);
        curY += 13;

        // 6. Time Created
        if (selected.getTimestamp() > 0) {
            this.drawString(ctx, StringUtils.translate("schematic_synchronizer.gui.placement_info.time"), contentX, curY, colorLabel);
            curY += 11;
            String timeStr = DATE_FORMAT.format(new Date(selected.getTimestamp()));
            this.drawString(ctx, timeStr, contentX + 4, curY, colorValue);
            curY += 13;
        }

        // Schematic metadata divider
        ServerSchematicInfo schem = ClientSchematicManager.getInstance().getSchematic(selected.getSchematicId());
        if (schem != null) {
            RenderUtils.drawRect(ctx, contentX, curY, maxTextW, 1, 0x40FFFFFF);
            curY += 6;

            if (schem.getAuthor() != null && !schem.getAuthor().isEmpty() && !schem.getAuthor().equals("?")) {
                this.drawString(ctx, "§7Автор: §f" + schem.getAuthor(), contentX, curY, colorLabel);
                curY += 11;
            }
            if (schem.getTotalBlocks() > 0) {
                this.drawString(ctx, "§7Блоков: §f" + schem.getTotalBlocks(), contentX, curY, colorLabel);
                curY += 11;
            }
            this.drawString(ctx, "§7Размер: §f" + schem.getFormattedSize(), contentX, curY, colorLabel);
        }
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            closeGui(true);
            return true;
        }
        if (event.key() == 257 || event.key() == 335) { // GLFW_KEY_ENTER
            PlayerPlacementInfo selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
            if (selected != null) {
                onPlacementDoubleClicked(selected);
                return true;
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
    protected WidgetListServerPlacements createListWidget(int listX, int listY) {
        return new WidgetListServerPlacements(listX, listY, getBrowserWidth(), getBrowserHeight(), this, this::onSelectionChange);
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
        return this.height - 58;
    }
}
