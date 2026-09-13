package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;

public class WidgetListGroupPlacements extends WidgetListBase<GroupPlacementData, WidgetGroupPlacementEntry> {
    private final GuiManageHologramGroup parent;

    public WidgetListGroupPlacements(int x, int y, int width, int height,
                                    GuiManageHologramGroup parent) {
        super(x, y, width, height, null);
        this.parent = parent;
        this.browserEntryHeight = 26;

        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        this.browserEntriesOffsetY = this.widgetSearchBar.getHeight() + 3;
    }

    public GuiManageHologramGroup getParentGui() {
        return this.parent;
    }

    @Override
    protected List<String> getEntryStringsForFilter(GroupPlacementData entry) {
        if (entry == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        list.add(entry.getName().toLowerCase(Locale.ROOT));
        list.add(entry.getSchematicId().toLowerCase(Locale.ROOT));
        return list;
    }

    @Override
    protected WidgetGroupPlacementEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, GroupPlacementData entry) {
        return new WidgetGroupPlacementEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<GroupPlacementData> getAllEntries() {
        HologramGroupData group = this.parent.getGroup();
        if (group == null) return Collections.emptyList();
        return group.getPlacements();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (this.getAllEntries().isEmpty()) {
            String notice = StringUtils.translate("schematic_synchronizer.gui.manage_group.no_placements");
            int nw = this.getStringWidth(notice);
            int nx = this.posX + (this.browserWidth - nw) / 2;
            int ny = this.posY + (this.browserHeight / 2) - 4;
            this.drawString(ctx, "§7" + notice, nx, ny, 0xFFAAAAAA);
        }
    }
}
