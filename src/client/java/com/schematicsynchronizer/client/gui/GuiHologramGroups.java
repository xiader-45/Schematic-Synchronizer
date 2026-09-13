package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class GuiHologramGroups extends GuiListBase<HologramGroupData, WidgetHologramGroupEntry, WidgetListHologramGroups> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public GuiHologramGroups() {
        this(null);
    }

    public GuiHologramGroups(Screen parent) {
        super(10, 30);
        this.title = StringUtils.translate("schematic_synchronizer.gui.title.hologram_groups");
        this.setParent(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reCreateButtons();
        ClientHologramGroupManager.getInstance().requestGroups();
    }

    private void reCreateButtons() {
        this.clearButtons();

        int y = this.height - 24;
        int x = 10;

        HologramGroupData rawSelected = (getListWidget() != null) ? getListWidget().getLastSelectedEntry() : null;
        HologramGroupData selected = null;
        if (rawSelected != null) {
            selected = ClientHologramGroupManager.getInstance().getGroupById(rawSelected.getId());
        }

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

        boolean isMember = (selected != null && myUuid != null && selected.isMember(myUuid));
        boolean isOwner = (selected != null && myUuid != null && selected.isOwner(myUuid));
        if (!isOwner && selected != null && mc.player != null && selected.getOwnerName() != null && selected.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) {
            isOwner = true;
        }
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();

        final HologramGroupData finalSelected = selected;
        final boolean finalIsOwner = isOwner;
        final boolean finalIsMember = isMember;
        final boolean finalIsOp = isOp;

        // Button 1: Dynamic Action (Join / Leave / Delete)
        String actionLabel;
        if (finalSelected == null) {
            actionLabel = StringUtils.translate("schematic_synchronizer.gui.button.join_group");
        } else if (finalIsOwner) {
            actionLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group");
        } else if (finalIsMember) {
            actionLabel = StringUtils.translate("schematic_synchronizer.gui.button.leave_group");
        } else {
            actionLabel = StringUtils.translate("schematic_synchronizer.gui.button.join_group");
        }

        int btnW = this.getStringWidth(actionLabel) + 20;
        ButtonGeneric btnAction = new ButtonGeneric(x, y, btnW, 20, actionLabel);
        btnAction.setEnabled(finalSelected != null);
        if (finalSelected == null) {
            btnAction.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.hover.select_group_first"));
        } else if (finalIsOwner) {
            btnAction.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group"));
        } else if (finalIsMember) {
            btnAction.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.leave_group"));
        }
        addButton(btnAction, (btn, mouse) -> {
            if (finalSelected != null) {
                if (finalIsOwner) {
                    GuiBase.openGui(new GuiConfirmOwnerLeave(this, finalSelected));
                } else if (finalIsMember) {
                    ClientHologramGroupManager.getInstance().leaveGroup(finalSelected.getId(), LeaveHologramGroupPayload.ACTION_LEAVE);
                    refreshList();
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "schematic_synchronizer.message.left_group", finalSelected.getName());
                } else {
                    ClientHologramGroupManager.getInstance().joinGroup(finalSelected.getId());
                    refreshList();
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "schematic_synchronizer.message.joined_group", finalSelected.getName());
                }
            }
        });
        x += btnW + 4;

        // Button 2: Manage Group
        String manLabel = StringUtils.translate("schematic_synchronizer.gui.button.manage_group");
        int manW = this.getStringWidth(manLabel) + 20;
        ButtonGeneric btnManage = new ButtonGeneric(x, y, manW, 20, manLabel);
        boolean canOpen = (finalSelected != null) && (finalIsMember || finalIsOwner || finalIsOp);
        btnManage.setEnabled(canOpen);
        if (!canOpen) {
            if (finalSelected == null) {
                btnManage.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.hover.select_group_first"));
            } else {
                btnManage.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.hover.join_to_manage"));
            }
        }
        addButton(btnManage, (btn, mouse) -> {
            if (finalSelected != null && (finalIsMember || finalIsOwner || finalIsOp)) {
                GuiBase.openGui(new GuiManageHologramGroup(this, finalSelected));
            }
        });
        x += manW + 4;

        // Button 2.5: OP Delete Group (with confirmation)
        if (finalSelected != null && finalIsOp && !finalIsOwner) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.button.delete_group_op");
            int delW = this.getStringWidth(delLabel) + 16;
            ButtonGeneric btnDel = new ButtonGeneric(x, y, delW, 20, delLabel);
            addButton(btnDel, (btn, mouse) -> {
                IConfirmationListener listener = new IConfirmationListener() {
                    @Override
                    public boolean onActionConfirmed() {
                        ClientHologramGroupManager.getInstance().leaveGroup(finalSelected.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
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
                        finalSelected.getName()
                );
                GuiBase.openGui(gui);
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
        return new WidgetListHologramGroups(listX, listY, getBrowserWidth(), getBrowserHeight(), this, null);
    }

    @Override
    protected int getBrowserWidth() {
        return (this.width - 24) * 3 / 5;
    }

    @Override
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

        // Schematic ID
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.file") + ": §f" + selected.getSchematicId(), contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Dimension
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.dimension") + ": §f" + selected.getDimension(), contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Owner
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.owner") + ": §f" + selected.getOwnerName(), contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Position
        String posStr = String.format("X: %d, Y: %d, Z: %d", selected.getOrigin().getX(), selected.getOrigin().getY(), selected.getOrigin().getZ());
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.pos") + ": §f" + posStr, contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Rotation & Mirror
        String rotMirStr = selected.getRotation() + " / " + selected.getMirror();
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.rot_mir") + ": §f" + rotMirStr, contentX, curY, 0xFFFFFFFF);
        curY += 12;

        // Timestamp
        if (selected.getLastModified() > 0) {
            String dateStr = DATE_FORMAT.format(new Date(selected.getLastModified()));
            this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.modified") + ": §f" + dateStr, contentX, curY, 0xFFFFFFFF);
            curY += 14;
        }

        // Members list
        this.drawString(ctx, "§e" + StringUtils.translate("schematic_synchronizer.gui.info.members") + " (" + selected.getMembers().size() + "):", contentX, curY, 0xFFFFFFFF);
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
