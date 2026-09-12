package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;

import java.util.*;

public class WidgetListGroupMembers extends WidgetListBase<Map.Entry<UUID, String>, WidgetGroupMemberEntry> {
    private final GuiManageGroupMembers parent;

    public WidgetListGroupMembers(int x, int y, int width, int height,
                                  GuiManageGroupMembers parent) {
        super(x, y, width, height, null);
        this.parent = parent;
        this.browserEntryHeight = 26;

        int searchWidth = Math.min(160, Math.max(100, width / 3));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 4, searchWidth, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.browserEntriesOffsetY = 22;
    }

    public GuiManageGroupMembers getParentGui() {
        return this.parent;
    }

    @Override
    protected List<String> getEntryStringsForFilter(Map.Entry<UUID, String> entry) {
        if (entry == null || entry.getValue() == null) return Collections.emptyList();
        return Collections.singletonList(entry.getValue().toLowerCase(Locale.ROOT));
    }

    @Override
    protected WidgetGroupMemberEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, Map.Entry<UUID, String> entry) {
        return new WidgetGroupMemberEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<Map.Entry<UUID, String>> getAllEntries() {
        HologramGroupData group = this.parent.getGroup();
        if (group == null) return Collections.emptyList();
        List<Map.Entry<UUID, String>> list = new ArrayList<>(group.getMembers().entrySet());
        list.sort((a, b) -> {
            boolean aOwner = group.isOwner(a.getKey());
            boolean bOwner = group.isOwner(b.getKey());
            if (aOwner != bOwner) return aOwner ? -1 : 1;
            return a.getValue().compareToIgnoreCase(b.getValue());
        });
        return list;
    }
}
