package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.UUID;

public class GuiManageGroupMembers extends GuiListBase<Map.Entry<UUID, String>, WidgetGroupMemberEntry, WidgetListGroupMembers> {
    private final String groupId;
    private HologramGroupData group;

    public GuiManageGroupMembers(Screen parent, HologramGroupData group) {
        super(10, 48);
        this.setParent(parent);
        this.groupId = group != null ? group.getId() : "";
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.manage_group.title");
    }

    public HologramGroupData getGroup() {
        if (this.groupId != null && !this.groupId.isEmpty()) {
            HologramGroupData latest = ClientHologramGroupManager.getInstance().getGroupById(this.groupId);
            if (latest != null) {
                this.group = latest;
            }
        }
        return this.group;
    }

    public boolean canManage() {
        HologramGroupData g = getGroup();
        if (g == null) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
        boolean isOwner = (myUuid != null && g.isOwner(myUuid));
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        return isOwner || isOp;
    }

    public void closeScreen() {
        this.closeGui(true);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reCreateButtons();
        ClientHologramGroupManager.getInstance().setGuiRefreshCallback(this::refreshList);
    }

    public void refreshList() {
        if (this.getListWidget() != null) {
            this.getListWidget().refreshEntries();
        }
        this.reCreateButtons();
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    @Override
    public void removed() {
        super.removed();
        ClientHologramGroupManager.getInstance().setGuiRefreshCallback(null);
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 76;
    }

    @Override
    protected WidgetListGroupMembers createListWidget(int listX, int listY) {
        return new WidgetListGroupMembers(listX, listY, getBrowserWidth(), getBrowserHeight(), this);
    }

    private void createButtons() {
        HologramGroupData g = getGroup();
        int tabY = 24;
        int x = 10;

        int placeCount = (g != null) ? g.getPlacements().size() : 0;
        int memberCount = (g != null) ? g.getMembers().size() : 0;

        // Tab 1: Placements
        String tabPlacementsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_placements", placeCount);
        int tabPW = this.getStringWidth(tabPlacementsLabel) + 16;
        ButtonGeneric btnTabP = new ButtonGeneric(x, tabY, tabPW, 20, tabPlacementsLabel);
        addButton(btnTabP, (btn, mouse) -> {
            GuiBase.openGui(new GuiManageHologramGroup(this.getParent(), this.getGroup()));
        });
        x += tabPW + 4;

        // Tab 2: Members (Active)
        String tabMembersLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_members", memberCount);
        int tabMW = this.getStringWidth(tabMembersLabel) + 16;
        ButtonGeneric btnTabM = new ButtonGeneric(x, tabY, tabMW, 20, tabMembersLabel);
        btnTabM.setEnabled(false);
        addButton(btnTabM, (btn, mouse) -> {});

        // Top Right: Take ownership (OP)
        if (g != null) {
            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
            boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
            boolean isOwner = (myUuid != null && g.isOwner(myUuid));
            if (isOp && !isOwner && myUuid != null) {
                String takeLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.take_ownership_op");
                int takeW = this.getStringWidth(takeLabel) + 16;
                ButtonGeneric btnTake = new ButtonGeneric(this.width - takeW - 10, tabY, takeW, 20, takeLabel);
                addButton(btnTake, (btn, mouse) -> {
                    ClientHologramGroupManager.getInstance().transferOwnership(g.getId(), myUuid);
                    closeGui(true);
                });
            }
        }

        // Bottom Bar
        int bottomY = this.height - 24;

        if (canManage() && g != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(10, bottomY, delW, 20, delLabel);
            btnDel.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group"));
            addButton(btnDel, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().leaveGroup(g.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
                closeGui(true);
            });
        }

        String backLabel = StringUtils.translate("gui.back");
        int backW = 80;
        ButtonGeneric btnBack = new ButtonGeneric(this.width - backW - 10, bottomY, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        HologramGroupData g = getGroup();
        String grpName = (g != null) ? g.getName() : "";
        String dimStr = (g != null) ? g.getDimension() : "";
        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.title_with_name", grpName) +
                "  §8|  §7" + dimStr;
        this.drawStringWithShadow(ctx, titleStr, 10, 9, 0xFFFFAA00);
    }
}
