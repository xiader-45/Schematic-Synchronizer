package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;

import java.util.*;

public class WidgetListHologramGroups extends WidgetListBase<HologramGroupData, WidgetHologramGroupEntry> {
    private final GuiHologramGroups parent;
    private static final Comparator<HologramGroupData> COMPARATOR =
            Comparator.comparingLong(HologramGroupData::getLastModified).reversed()
                    .thenComparing(HologramGroupData::getName, String.CASE_INSENSITIVE_ORDER);

    public WidgetListHologramGroups(int x, int y, int width, int height,
                                    GuiHologramGroups parent,
                                    ISelectionListener<HologramGroupData> selectionListener) {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.browserEntryHeight = 24;

        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        this.browserEntriesOffsetY = this.widgetSearchBar.getHeight() + 3;
    }

    public GuiHologramGroups getParentGui() {
        return this.parent;
    }

    public int getLastSelectedEntryIndex() {
        return this.lastSelectedEntryIndex;
    }

    @Override
    protected List<String> getEntryStringsForFilter(HologramGroupData entry) {
        if (entry == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        list.add(entry.getName().toLowerCase(Locale.ROOT));
        list.add(entry.getSchematicId().toLowerCase(Locale.ROOT));
        list.add(entry.getOwnerName().toLowerCase(Locale.ROOT));
        list.add(entry.getDimension().toLowerCase(Locale.ROOT));
        for (GroupPlacementData p : entry.getPlacements()) {
            list.add(p.getName().toLowerCase(Locale.ROOT));
            list.add(p.getSchematicId().toLowerCase(Locale.ROOT));
        }
        return list;
    }

    @Override
    protected WidgetHologramGroupEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, HologramGroupData entry) {
        return new WidgetHologramGroupEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<HologramGroupData> getAllEntries() {
        List<HologramGroupData> all = new ArrayList<>(ClientHologramGroupManager.getInstance().getGroups());
        all.sort(COMPARATOR);
        return all;
    }
}
