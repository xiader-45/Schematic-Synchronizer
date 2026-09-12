package com.schematicsynchronizer.client.gui;

import com.google.common.collect.ImmutableList;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;

public class WidgetListServerPlacements extends WidgetListBase<PlayerPlacementInfo, WidgetServerPlacementEntry> {
    private final GuiServerPlacementsList parent;

    public WidgetListServerPlacements(int x, int y, int width, int height,
                                    GuiServerPlacementsList parent,
                                    ISelectionListener<PlayerPlacementInfo> selectionListener) {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.browserEntryHeight = 22;

        int searchWidth = Math.min(140, Math.max(80, width / 3));
        int searchX = x + width - searchWidth - 4;
        this.widgetSearchBar = new WidgetSearchBar(searchX, y + 4, searchWidth, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.browserEntriesOffsetY = 22;
    }

    public GuiServerPlacementsList getParentGui() {
        return this.parent;
    }

    @Override
    protected List<String> getEntryStringsForFilter(PlayerPlacementInfo entry) {
        if (entry == null) {
            return Collections.emptyList();
        }
        return ImmutableList.of(
                entry.getSchematicId().toLowerCase(Locale.ROOT),
                entry.getOwnerName().toLowerCase(Locale.ROOT),
                entry.getDimension().toLowerCase(Locale.ROOT),
                entry.getPos().toShortString()
        );
    }

    @Override
    protected WidgetServerPlacementEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, PlayerPlacementInfo entry) {
        return new WidgetServerPlacementEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, this);
    }

    @Override
    protected Collection<PlayerPlacementInfo> getAllEntries() {
        List<PlayerPlacementInfo> all = new ArrayList<>();
        String filterSchem = this.parent.getFilterSchematicId();

        if (filterSchem != null && !filterSchem.isEmpty()) {
            all.addAll(ClientSchematicManager.getInstance().getPlacementsForSchematic(filterSchem));
        } else {
            all.addAll(ClientSchematicManager.getInstance().getAllPlacements());
        }

        String filter = this.getFilterText();
        if (filter != null && !filter.trim().isEmpty()) {
            String search = filter.trim().toLowerCase(Locale.ROOT);
            List<PlayerPlacementInfo> filtered = new ArrayList<>();
            for (PlayerPlacementInfo p : all) {
                if (p.getSchematicId().toLowerCase(Locale.ROOT).contains(search)
                        || p.getOwnerName().toLowerCase(Locale.ROOT).contains(search)
                        || p.getDimension().toLowerCase(Locale.ROOT).contains(search)
                        || p.getPos().toShortString().contains(search)) {
                    filtered.add(p);
                }
            }
            filtered.sort(Comparator.comparing(PlayerPlacementInfo::getOwnerName, String.CASE_INSENSITIVE_ORDER));
            return filtered;
        }

        all.sort(Comparator.comparing(PlayerPlacementInfo::getOwnerName, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        if (this.listContents.isEmpty()) {
            int cy = this.posY + this.totalHeight / 2 - 10;
            String text = StringUtils.translate("schematic_synchronizer.gui.placement_info.empty_list");
            int w = this.getStringWidth(text);
            int cx = this.posX + (this.totalWidth - w) / 2;
            this.drawStringWithShadow(ctx, text, cx, cy, 0xFFAAAAAA);
        }
    }
}
