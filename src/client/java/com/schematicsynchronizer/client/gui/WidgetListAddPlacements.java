package com.schematicsynchronizer.client.gui;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;

import java.util.*;

public class WidgetListAddPlacements extends WidgetListBase<SchematicPlacement, WidgetAddPlacementEntry> {
    private final GuiAddPlacementsToGroup parent;

    public WidgetListAddPlacements(int x, int y, int width, int height,
                                  GuiAddPlacementsToGroup parent,
                                  ISelectionListener<SchematicPlacement> selectionListener) {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.browserEntryHeight = 22;

        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        this.browserEntriesOffsetY = this.widgetSearchBar.getHeight() + 3;
    }

    public GuiAddPlacementsToGroup getParentGui() {
        return this.parent;
    }

    @Override
    protected Collection<SchematicPlacement> getAllEntries() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
    }

    @Override
    protected List<String> getEntryStringsForFilter(SchematicPlacement entry) {
        if (entry == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        list.add(entry.getName().toLowerCase(Locale.ROOT));
        if (entry.getSchematic() != null && entry.getSchematic().getFile() != null) {
            list.add(entry.getSchematic().getFile().getFileName().toString().toLowerCase(Locale.ROOT));
        }
        return list;
    }

    @Override
    protected WidgetAddPlacementEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, SchematicPlacement entry) {
        boolean alreadyInGroup = this.parent.isAlreadyInGroup(entry);
        return new WidgetAddPlacementEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry),
                entry, listIndex, this, alreadyInGroup);
    }
}

