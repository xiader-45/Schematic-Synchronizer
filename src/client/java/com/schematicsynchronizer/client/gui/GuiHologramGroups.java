package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class GuiHologramGroups extends GuiListBase<HologramGroupData, WidgetHologramGroupEntry, WidgetListHologramGroups> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public GuiHologramGroups(Screen parent) {
        super(10, 26);
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_synchronizer.gui.title.hologram_groups");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reCreateButtons();
        ClientHologramGroupManager.getInstance().setGuiRefreshCallback(this::refreshList);
        ClientHologramGroupManager.getInstance().requestGroups();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    private void createButtons() {
        int y = this.height - 24;
        int x = 10;

        HologramGroupData selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;

        boolean isMember = (selected != null && myUuid != null && selected.isMember(myUuid));
        boolean isOwner = (selected != null && myUuid != null && selected.isOwner(myUuid));
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean canManage = isOwner || isOp;

        // Button 1: Join / Leave
        if (selected != null && isMember) {
            String leaveLabel = StringUtils.translate("schematic_synchronizer.gui.button.leave_group");
            int leaveW = this.getStringWidth(leaveLabel) + 20;
            ButtonGeneric btnLeave = new ButtonGeneric(x, y, leaveW, 20, leaveLabel);
            addButton(btnLeave, (btn, mouse) -> {
                if (isOwner) {
                    GuiBase.openGui(new GuiConfirmOwnerLeave(this, selected));
                } else {
                    ClientHologramGroupManager.getInstance().leaveGroup(selected.getId(), LeaveHologramGroupPayload.ACTION_TRANSFER_RANDOM);
                }
            });
            x += leaveW + 4;
        } else if (selected != null) {
            String joinLabel = StringUtils.translate("schematic_synchronizer.gui.button.join_group");
            int joinW = this.getStringWidth(joinLabel) + 20;
            ButtonGeneric btnJoin = new ButtonGeneric(x, y, joinW, 20, joinLabel);
            addButton(btnJoin, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().joinGroup(selected.getId());
            });
            x += joinW + 4;
        }

        // Button 2: Manage Group
        String manLabel = StringUtils.translate("schematic_synchronizer.gui.button.manage_group");
        int manW = this.getStringWidth(manLabel) + 20;
        ButtonGeneric btnManage = new ButtonGeneric(x, y, manW, 20, manLabel);
        btnManage.setEnabled(selected != null && canManage);
        addButton(btnManage, (btn, mouse) -> {
            if (selected != null && canManage) {
                GuiBase.openGui(new GuiManageHologramGroup(this, selected));
            }
        });
        x += manW + 4;

        // Button 2.5: OP Delete Group
        if (selected != null && isOp && !isOwner) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.button.delete_group_op");
            int delW = this.getStringWidth(delLabel) + 16;
            ButtonGeneric btnDel = new ButtonGeneric(x, y, delW, 20, delLabel);
            addButton(btnDel, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().leaveGroup(selected.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
            });
            x += delW + 4;
        }

        // Button 3: Create Group
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.button.create_group");
        int createW = this.getStringWidth(createLabel) + 16;
        ButtonGeneric btnCreate = new ButtonGeneric(x, y, createW, 20, createLabel);
        addButton(btnCreate, (btn, mouse) -> {
            GuiBase.openGui(new GuiCreateHologramGroup(this, null));
        });
        x += createW + 4;

        // Button 4: Refresh
        String refLabel = StringUtils.translate("litematica.gui.button.material_list.refresh_list");
        int refW = this.getStringWidth(refLabel) + 16;
        ButtonGeneric btnRef = new ButtonGeneric(x, y, refW, 20, refLabel);
        addButton(btnRef, (btn, mouse) -> ClientHologramGroupManager.getInstance().requestGroups());

        // Button 5: Back (Right aligned)
        String backLabel = StringUtils.translate("gui.back");
        int backW = this.getStringWidth(backLabel) + 20;
        int backX = this.width - backW - 10;
        ButtonGeneric btnBack = new ButtonGeneric(backX, y, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    public void refreshList() {
        if (this.getListWidget() != null) {
            this.getListWidget().refreshEntries();
        }
        this.reCreateButtons();
    }

    public void onSelectionChange(HologramGroupData entry) {
        this.reCreateButtons();
    }

    @Override
    protected WidgetListHologramGroups createListWidget(int listX, int listY) {
        int listWidth = getBrowserWidth();
        int listHeight = getBrowserHeight();
        return new WidgetListHologramGroups(listX, listY, listWidth, listHeight, this, null);
    }

    protected int getBrowserWidth() {
        return (this.width - 24) * 3 / 5;
    }

    protected int getBrowserHeight() {
        return this.height - 56;
    }

    private int getInfoWidth() {
        return (this.width - 24) * 2 / 5;
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawSelectedEntryInfo(ctx, mouseX, mouseY);
    }

    private void drawSelectedEntryInfo(GuiContext ctx, int mouseX, int mouseY) {
        int boxX = 10 + getBrowserWidth() + 4;
        int boxY = 26;
        int boxW = getInfoWidth();
        int boxH = getBrowserHeight();

        RenderUtils.drawOutlinedBox(ctx, boxX, boxY, boxW, boxH, 0xA0000000, 0xFF999999);

        HologramGroupData selected = getListWidget() != null ? getListWidget().getLastSelectedEntry() : null;
        int curY = boxY + 6;
        int contentX = boxX + 6;

        if (selected == null) {
            String title = StringUtils.translate("schematic_synchronizer.gui.hologram_groups.info.title");
            this.drawStringWithShadow(ctx, "§6§l" + title, contentX, curY, 0xFFFFAA00);
            curY += 14;
            this.drawString(ctx, "§7" + StringUtils.translate("schematic_synchronizer.gui.hologram_groups.info.select_group"), contentX, curY, 0xFFAAAAAA);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
        boolean isOwner = (myUuid != null && selected.isOwner(myUuid));
        boolean isMember = (myUuid != null && selected.isMember(myUuid));

        // Group Title
        this.drawStringWithShadow(ctx, "§6§l" + selected.getName(), contentX, curY, 0xFFFFAA00);
        curY += 16;

        // Status
        String statusStr;
        if (isOwner) {
            statusStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner");
        } else if (isMember) {
            statusStr = "§a§l" + StringUtils.translate("schematic_synchronizer.gui.group.status.member");
        } else {
            statusStr = "§7" + StringUtils.translate("schematic_synchronizer.gui.group.status.not_member");
        }
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.status") + ": " + statusStr, contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Dimension
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.dimension") + ": §f" + selected.getDimension(), contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Owner
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.owner") + ": §f" + selected.getOwnerName(), contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Timestamp
        if (selected.getLastModified() > 0) {
            String dateStr = DATE_FORMAT.format(new Date(selected.getLastModified()));
            this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.modified") + ": §f" + dateStr, contentX, curY, 0xFFFFFFFF);
            curY += 14;
        }

        // Section: Placements list
        List<GroupPlacementData> placements = selected.getPlacements();
        String plTitle = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_placements", placements.size()) + ":";
        this.drawString(ctx, plTitle, contentX, curY, 0xFFFFAA00);
        curY += 12;

        if (placements.isEmpty()) {
            this.drawString(ctx, "  §8(" + StringUtils.translate("schematic_synchronizer.gui.manage_group.no_placements_short") + ")", contentX, curY, 0xFF888888);
            curY += 12;
        } else {
            for (GroupPlacementData p : placements) {
                if (curY > boxY + boxH - 46) break;
                BlockPos pos = p.getOrigin();
                String pHeader = "  §f• " + p.getName() + " §7(" + p.getSchematicId() + ")";
                this.drawString(ctx, pHeader, contentX, curY, 0xFFFFFFFF);
                curY += 10;
                String pCoords = String.format("    §8X:%d Y:%d Z:%d  Rot:%s  Mir:%s", pos.getX(), pos.getY(), pos.getZ(), p.getRotation(), p.getMirror());
                this.drawString(ctx, pCoords, contentX, curY, 0xFF888888);
                curY += 12;
            }
        }
        curY += 4;

        // Section: Members list
        String memTitle = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_members", selected.getMembers().size()) + ":";
        this.drawString(ctx, memTitle, contentX, curY, 0xFFFFAA00);
        curY += 12;

        for (String memberName : selected.getMembers().values()) {
            if (curY > boxY + boxH - 12) break;
            boolean isThisOwner = memberName.equalsIgnoreCase(selected.getOwnerName());
            String tag = isThisOwner ? " §6(" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + ")" : "";
            this.drawString(ctx, "  §7• §f" + memberName + tag, contentX, curY, 0xFFFFFFFF);
            curY += 11;
        }
    }
}
