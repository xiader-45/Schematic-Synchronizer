package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
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

        int searchWidth = Math.min(140, Math.max(80, width / 3));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 4, searchWidth, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.browserEntriesOffsetY = 22;
    }

    public GuiHologramGroups getParentGui() {
        return this.parent;
    }

    @Override
    protected List<String> getEntryStringsForFilter(HologramGroupData entry) {
        if (entry == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        list.add(entry.getName().toLowerCase(Locale.ROOT));
        list.add(entry.getSchematicId().toLowerCase(Locale.ROOT));
        list.add(entry.getOwnerName().toLowerCase(Locale.ROOT));
        list.add(entry.getDimension().toLowerCase(Locale.ROOT));
        return list;
    }

    @Override
    protected WidgetHologramGroupEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, HologramGroupData entry) {
        return new WidgetHologramGroupEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<HologramGroupData> getAllEntries() {
        List<HologramGroupData> all = new ArrayList<>(ClientHologramGroupManager.getInstance().getGroups());

        String filter = this.getFilterText();
        if (filter != null && !filter.trim().isEmpty()) {
            String search = filter.trim().toLowerCase(Locale.ROOT);
            List<HologramGroupData> filtered = new ArrayList<>();
            for (HologramGroupData g : all) {
                if (g.getName().toLowerCase(Locale.ROOT).contains(search)
                        || g.getSchematicId().toLowerCase(Locale.ROOT).contains(search)
                        || g.getOwnerName().toLowerCase(Locale.ROOT).contains(search)) {
                    filtered.add(g);
                }
            }
            all = filtered;
        }

        all.sort(COMPARATOR);
        return all;
    }
}
