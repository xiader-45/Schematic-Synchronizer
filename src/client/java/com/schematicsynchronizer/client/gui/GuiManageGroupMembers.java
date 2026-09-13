package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.UUID;

public class GuiManageGroupMembers extends GuiListBase<Map.Entry<UUID, String>, WidgetGroupMemberEntry, WidgetListGroupMembers> {
    private final HologramGroupData group;

    public GuiManageGroupMembers(Screen parent, HologramGroupData group) {
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
        this.reCreateButtons();
    }

    public boolean canManage() {
        HologramGroupData g = getGroup();
        if (g == null) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        if (myUuid == null) return false;
        if (g.isOwner(myUuid)) return true;
        if (mc.player != null && g.getOwnerName() != null && g.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) return true;
        return ClientHologramGroupManager.getInstance().canOpManage();
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
        x += tabMW + 4;

        // Tab 3: Settings
        String tabSettingsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_settings");
        int tabSW = this.getStringWidth(tabSettingsLabel) + 16;
        ButtonGeneric btnTabS = new ButtonGeneric(x, tabY, tabSW, 20, tabSettingsLabel);
        addButton(btnTabS, (btn, mouse) -> {
            GuiBase.openGui(new GuiManageGroupSettings(this.getParent(), this.getGroup()));
        });

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean isOwner = (g != null && myUuid != null && g.isOwner(myUuid));
        if (!isOwner && mc.player != null && g != null && g.getOwnerName() != null && g.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) {
            isOwner = true;
        }

        // Top Right: Take ownership (OP)
        if (g != null && isOp && !isOwner && myUuid != null) {
            String takeLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.take_ownership_op");
            int takeW = this.getStringWidth(takeLabel) + 16;
            ButtonGeneric btnTake = new ButtonGeneric(this.width - takeW - 10, tabY, takeW, 20, takeLabel);
            addButton(btnTake, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().transferOwnership(g.getId(), myUuid);
                if (this.getParent() != null) {
                    GuiBase.openGui(this.getParent());
                } else {
                    this.closeGui(true);
                }
            });
        }

        // Bottom Bar
        int bottomY = this.height - 24;

        if (isOwner && g != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(10, bottomY, delW, 20, delLabel);
            btnDel.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group"));
            addButton(btnDel, (btn, mouse) -> {
                GuiBase.openGui(new GuiConfirmOwnerLeave(this, g));
            });
        } else if (isOp && g != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.button.delete_group_op");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(10, bottomY, delW, 20, delLabel);
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
        } else if (g != null && myUuid != null && g.isMember(myUuid)) {
            String leaveLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.leave_group");
            int leaveW = this.getStringWidth(leaveLabel) + 20;
            ButtonGeneric btnLeave = new ButtonGeneric(10, bottomY, leaveW, 20, leaveLabel);
            btnLeave.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.leave_group"));
            addButton(btnLeave, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().leaveGroup(g.getId(), LeaveHologramGroupPayload.ACTION_LEAVE);
                if (this.getParent() != null) {
                    GuiBase.openGui(this.getParent());
                } else {
                    this.closeGui(true);
                }
            });
        }

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
    protected int getBrowserWidth() {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 56;
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        HologramGroupData g = getGroup();
        String dimStr = (g != null && g.getDimension() != null && !g.getDimension().isEmpty()) ? "  §8|  §7" + g.getDimension() : "";
        this.drawString(ctx, this.getTitleString() + dimStr, 20, 10, -1);
    }

    @Override
    protected WidgetListGroupMembers createListWidget(int listX, int listY) {
        return new WidgetListGroupMembers(listX, listY, getBrowserWidth(), getBrowserHeight(), this);
    }
}
