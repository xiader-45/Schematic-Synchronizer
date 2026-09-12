package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;

import java.util.*;

public class WidgetListGroupPlacements extends WidgetListBase<GroupPlacementData, WidgetGroupPlacementEntry> {
    private final GuiManageHologramGroup parent;

    public WidgetListGroupPlacements(int x, int y, int width, int height,
                                    GuiManageHologramGroup parent) {
        super(x, y, width, height, null);
        this.parent = parent;
        this.browserEntryHeight = 26;

        int searchWidth = Math.min(160, Math.max(100, width / 3));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 4, searchWidth, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.browserEntriesOffsetY = 22;
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
}
