package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

public class GuiManageHologramGroup extends GuiListBase<GroupPlacementData, WidgetGroupPlacementEntry, WidgetListGroupPlacements> {
    private final HologramGroupData group;
    private boolean lastCanAdd = false;
    private boolean lastCanManage = false;
    private long lastGroupModified = -1;

    public GuiManageHologramGroup(Screen parent, HologramGroupData group) {
        super(10, 48);
        this.setParent(parent);
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.manage_group.title_named", group != null ? group.getName() : "");
    }

    public HologramGroupData getGroup() {
        if (this.group == null) return null;
        HologramGroupData latest = ClientHologramGroupManager.getInstance().getGroupById(this.group.getId());
        return latest != null ? latest : this.group;
    }

    public boolean canManage() {
        HologramGroupData g = getGroup();
        if (g == null) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);
        if (myUuid == null && myName == null) return false;
        if (g.isOwner(myUuid, myName)) return true;
        return ClientHologramGroupManager.getInstance().canOpManage();
    }

    public boolean canAddPlacements() {
        HologramGroupData g = getGroup();
        if (g == null) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);
        if (myUuid == null && myName == null) return false;
        if (canManage()) return true;
        String perm = g.getMemberPermission(myUuid, myName);
        return HologramGroupData.PERM_ALL.equals(perm)
                || HologramGroupData.PERM_ADD.equals(perm);
    }

    public void reCreateButtons() {
        this.clearButtons();
        this.createButtons();
    }

    public void refreshList() {
        if (getListWidget() != null) {
            getListWidget().refreshEntries();
        }
        this.reCreateButtons();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.lastCanAdd = canAddPlacements();
        this.lastCanManage = canManage();
        HologramGroupData g = getGroup();
        this.lastGroupModified = g != null ? g.getLastModified() : -1;
        this.reCreateButtons();
        ClientHologramGroupManager.getInstance().setGuiRefreshCallback(this::refreshList);
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
    protected WidgetListGroupPlacements createListWidget(int listX, int listY) {
        return new WidgetListGroupPlacements(listX, listY, getBrowserWidth(), getBrowserHeight(), this);
    }

    private void createButtons() {
        HologramGroupData g = getGroup();
        int tabY = 24;
        int x = 10;

        int placeCount = (g != null) ? g.getPlacements().size() : 0;
        int memberCount = (g != null) ? g.getMembers().size() : 0;

        // Tab 1: Placements (Active)
        String tabPlacementsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_placements", placeCount);
        int tabPW = this.getStringWidth(tabPlacementsLabel) + 16;
        ButtonGeneric btnTabP = new ButtonGeneric(x, tabY, tabPW, 20, tabPlacementsLabel);
        btnTabP.setEnabled(false);
        addButton(btnTabP, (btn, mouse) -> {});
        x += tabPW + 4;

        // Tab 2: Members
        String tabMembersLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_members", memberCount);
        int tabMW = this.getStringWidth(tabMembersLabel) + 16;
        ButtonGeneric btnTabM = new ButtonGeneric(x, tabY, tabMW, 20, tabMembersLabel);
        addButton(btnTabM, (btn, mouse) -> {
            GuiBase.openGui(new GuiManageGroupMembers(this.getParent(), this.getGroup()));
        });
        x += tabMW + 4;

        // Tab 3: Settings
        String tabSettingsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_settings");
        int tabSW = this.getStringWidth(tabSettingsLabel) + 16;
        ButtonGeneric btnTabS = new ButtonGeneric(x, tabY, tabSW, 20, tabSettingsLabel);
        addButton(btnTabS, (btn, mouse) -> {
            GuiBase.openGui(new GuiManageGroupSettings(this.getParent(), this.getGroup()));
        });

        // Bottom Bar
        int bottomY = this.height - 24;
        int bottomX = 10;

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);

        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean isOwner = (g != null && g.isOwner(myUuid, myName));

        if (isOwner && g != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(bottomX, bottomY, delW, 20, delLabel);
            btnDel.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group"));
            addButton(btnDel, (btn, mouse) -> {
                GuiBase.openGui(new GuiConfirmOwnerLeave(this, g));
            });
            bottomX += delW + 4;
        } else if (isOp && g != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.button.delete_group_op");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(bottomX, bottomY, delW, 20, delLabel);
            addButton(btnDel, (btn, mouse) -> {
                IConfirmationListener listener = new IConfirmationListener() {
                    @Override
                    public boolean onActionConfirmed() {
                        ClientHologramGroupManager.getInstance().leaveGroup(g.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
                        return true;
                    }

                    @Override
                    public boolean onActionCancelled() {
                        return true;
                    }
                };
                GuiConfirmAction gui = new GuiConfirmAction(
                        280,
                        "schematic_synchronizer.gui.confirm_delete_group.title",
                        listener,
                        this,
                        "schematic_synchronizer.gui.confirm_delete_group.message",
                        g.getName()
                );
                GuiBase.openGui(gui);
            });
            bottomX += delW + 4;
        } else if (g != null && g.isMember(myUuid, myName)) {
            String leaveLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.leave_group");
            int leaveW = this.getStringWidth(leaveLabel) + 20;
            ButtonGeneric btnLeave = new ButtonGeneric(bottomX, bottomY, leaveW, 20, leaveLabel);
            btnLeave.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.leave_group"));
            addButton(btnLeave, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().leaveGroup(g.getId(), LeaveHologramGroupPayload.ACTION_LEAVE);
                InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "schematic_synchronizer.message.left_group", g.getName());
                if (this.getParent() != null) {
                    GuiBase.openGui(this.getParent());
                } else {
                    this.closeGui(true);
                }
            });
            bottomX += leaveW + 4;
        }

        // Add Placement button: placed at the bottom bar next to delete / leave group!
        if (canAddPlacements() && g != null) {
            String addLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.add_placement");
            int addW = this.getStringWidth(addLabel) + 16;
            ButtonGeneric btnAddPlacement = new ButtonGeneric(bottomX, bottomY, addW, 20, addLabel);
            addButton(btnAddPlacement, (btn, mouse) -> {
                GuiBase.openGui(new GuiAddPlacementsToGroup(this, g));
            });
            bottomX += addW + 4;
        }

        // Back Button (Right aligned)
        String backLabel = StringUtils.translate("gui.back");
        int backW = 80;
        ButtonGeneric btnBack = new ButtonGeneric(this.width - backW - 10, bottomY, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> {
            if (this.getParent() != null) {
                GuiBase.openGui(this.getParent());
            } else {
                this.closeGui(true);
            }
        });
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        boolean curCanAdd = canAddPlacements();
        boolean curCanManage = canManage();
        HologramGroupData g = getGroup();
        long curModified = g != null ? g.getLastModified() : -1;
        if (curCanAdd != this.lastCanAdd || curCanManage != this.lastCanManage || curModified != this.lastGroupModified) {
            this.lastCanAdd = curCanAdd;
            this.lastCanManage = curCanManage;
            this.lastGroupModified = curModified;
            this.reCreateButtons();
            if (this.getListWidget() != null) {
                this.getListWidget().refreshEntries();
            }
        }
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        HologramGroupData g = getGroup();
        String dimStr = (g != null && g.getDimension() != null && !g.getDimension().isEmpty()) ? "  §8|  §7" + g.getDimension() : "";
        this.drawString(ctx, this.getTitleString() + dimStr, 20, 10, -1);
    }
}
