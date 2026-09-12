package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GuiSelectGroupForPlacement extends GuiBase {
    private final SchematicPlacement placement;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 4;

    public GuiSelectGroupForPlacement(Screen parent, SchematicPlacement placement) {
        this.setParent(parent);
        this.placement = placement;
        this.title = StringUtils.translate("schematic_synchronizer.gui.select_group.title");
    }

    private List<HologramGroupData> getManageableGroups() {
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();

        List<HologramGroupData> list = new ArrayList<>();
        for (HologramGroupData g : ClientHologramGroupManager.getInstance().getGroups()) {
            if (isOp || (myUuid != null && g.isOwner(myUuid))) {
                list.add(g);
            }
        }
        return list;
    }

    private boolean isPlacementAlreadyInGroup(HologramGroupData group) {
        if (group == null || this.placement == null) return false;
        for (GroupPlacementData p : group.getPlacements()) {
            if (p.getName().equalsIgnoreCase(this.placement.getName()) &&
                    p.getOrigin().equals(this.placement.getOrigin())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.createButtons();
    }

    private void createButtons() {
        this.clearButtons();

        int dialogW = 400;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        List<HologramGroupData> groups = getManageableGroups();
        int maxPages = Math.max(1, (int) Math.ceil((double) groups.size() / ITEMS_PER_PAGE));
        if (this.page >= maxPages) {
            this.page = maxPages - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }

        int itemY = y + 42;
        int startIndex = this.page * ITEMS_PER_PAGE;
        int endIndex = Math.min(groups.size(), startIndex + ITEMS_PER_PAGE);

        for (int i = startIndex; i < endIndex; i++) {
            HologramGroupData group = groups.get(i);
            boolean alreadyIn = isPlacementAlreadyInGroup(group);

            String btnLabel = alreadyIn
                    ? StringUtils.translate("schematic_synchronizer.gui.select_group.already_in")
                    : StringUtils.translate("schematic_synchronizer.gui.select_group.select_btn");
            int btnW = this.getStringWidth(btnLabel) + 16;
            int btnX = x + dialogW - btnW - 14;

            ButtonGeneric btnSelect = new ButtonGeneric(btnX, itemY + 4, btnW, 20, btnLabel);
            btnSelect.setEnabled(!alreadyIn);
            addButton(btnSelect, (btn, mouse) -> {
                addPlacementToGroup(group);
                closeGui(true);
            });

            itemY += 28;
        }

        // Pagination buttons if needed
        if (maxPages > 1) {
            int pageY = y + dialogH - 56;
            ButtonGeneric btnPrev = new ButtonGeneric(x + 14, pageY, 24, 18, "<");
            btnPrev.setEnabled(this.page > 0);
            addButton(btnPrev, (btn, mouse) -> {
                this.page--;
                this.createButtons();
            });

            ButtonGeneric btnNext = new ButtonGeneric(x + 42, pageY, 24, 18, ">");
            btnNext.setEnabled(this.page < maxPages - 1);
            addButton(btnNext, (btn, mouse) -> {
                this.page++;
                this.createButtons();
            });
        }

        // Bottom action buttons
        int btnY = y + dialogH - 28;

        // Button 1: Create New Group
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.select_group.create_new");
        int createW = this.getStringWidth(createLabel) + 16;
        ButtonGeneric btnCreate = new ButtonGeneric(x + 14, btnY, createW, 20, createLabel);
        addButton(btnCreate, (btn, mouse) -> {
            GuiBase.openGui(new GuiCreateHologramGroup(this.getParent(), this.placement));
        });

        // Button 2: Cancel
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = this.getStringWidth(cancelLabel) + 20;
        ButtonGeneric btnCancel = new ButtonGeneric(x + dialogW - cancelW - 14, btnY, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> closeGui(true));
    }

    private void addPlacementToGroup(HologramGroupData group) {
        if (group == null || this.placement == null) return;

        String schemId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.placement);
        if (schemId == null || schemId.isEmpty()) {
            if (this.placement.getSchematic() != null && this.placement.getSchematic().getFile() != null) {
                schemId = this.placement.getSchematic().getFile().getFileName().toString();
            }
        }
        if (schemId == null) schemId = "";

        String pId = UUID.randomUUID().toString().substring(0, 8);
        GroupPlacementData pData = new GroupPlacementData(
                pId,
                this.placement.getName(),
                schemId,
                this.placement.getOrigin(),
                this.placement.getRotation().name(),
                this.placement.getMirror().name(),
                this.placement.isLocked()
        );

        ClientHologramGroupManager.getInstance().registerPlacementForGroup(group.getId(), pId, this.placement.getHashId());
        ClientHologramGroupManager.getInstance().addPlacementsToGroup(group.getId(), Collections.singletonList(pData));
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 400;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 400;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String pName = this.placement != null ? this.placement.getName() : "";
        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.select_group.title", pName);
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 10, 0xFFFFAA00);

        String subtitle = "§7" + StringUtils.translate("schematic_synchronizer.gui.select_group.subtitle");
        this.drawString(ctx, subtitle, x + 14, y + 24, 0xFFAAAAAA);

        List<HologramGroupData> groups = getManageableGroups();
        if (groups.isEmpty()) {
            String emptyHint = "§8" + StringUtils.translate("schematic_synchronizer.gui.select_group.no_groups");
            this.drawString(ctx, emptyHint, x + 14, y + 60, 0xFF888888);
        } else {
            int itemY = y + 42;
            int startIndex = this.page * ITEMS_PER_PAGE;
            int endIndex = Math.min(groups.size(), startIndex + ITEMS_PER_PAGE);

            for (int i = startIndex; i < endIndex; i++) {
                HologramGroupData group = groups.get(i);
                RenderUtils.drawOutlinedBox(ctx, x + 12, itemY, dialogW - 24, 26, 0x28FFFFFF, 0x50AAAAAA);

                String gName = "§f§l" + group.getName();
                this.drawString(ctx, gName, x + 18, itemY + 4, 0xFFFFFFFF);

                int pCount = group.getPlacements().size();
                int mCount = group.getMembers().size();
                String info = "§7" + StringUtils.translate("schematic_synchronizer.gui.group.placements_count", pCount) +
                        " §8| §7" + StringUtils.translate("schematic_synchronizer.gui.group.members_count", mCount);
                this.drawString(ctx, info, x + 18, itemY + 14, 0xFFAAAAAA);

                itemY += 28;
            }

            int maxPages = Math.max(1, (int) Math.ceil((double) groups.size() / ITEMS_PER_PAGE));
            if (maxPages > 1) {
                int pageY = y + dialogH - 52;
                String pageStr = String.format("§8%d / %d", this.page + 1, maxPages);
                this.drawString(ctx, pageStr, x + 72, pageY, 0xFF888888);
            }
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
