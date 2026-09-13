package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

public class GuiConfirmOwnerLeave extends GuiBase {
    private final HologramGroupData group;

    public GuiConfirmOwnerLeave(Screen parent, HologramGroupData group) {
        this.setParent(parent);
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.owner_leave.title");
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogW = 320;
        int dialogH = 110;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        int btnY = y + dialogH - 28;

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

        int otherMembersCount = 0;
        if (this.group != null) {
            for (UUID uid : this.group.getMembers().keySet()) {
                if (!uid.equals(myUuid) && !uid.equals(this.group.getOwnerUuid())) {
                    otherMembersCount++;
                }
            }
        }

        int btnDelW = 100;
        int btnTrW = 114;
        int btnCancelW = 60;
        int spacing = 4;
        int totalBtnW = btnDelW + spacing + btnTrW + spacing + btnCancelW;
        int btnStartX = x + (dialogW - totalBtnW) / 2;

        // Button 1: Delete Group
        String delLabel = StringUtils.translate("schematic_synchronizer.gui.owner_leave.delete_group");
        ButtonGeneric btnDelete = new ButtonGeneric(btnStartX, btnY, btnDelW, 20, delLabel);
        addButton(btnDelete, (btn, mouse) -> finishAction(LeaveHologramGroupPayload.ACTION_DELETE));

        // Button 2: Transfer & Leave
        String trLabel = StringUtils.translate("schematic_synchronizer.gui.owner_leave.transfer_random");
        ButtonGeneric btnTransfer = new ButtonGeneric(btnStartX + btnDelW + spacing, btnY, btnTrW, 20, trLabel);
        btnTransfer.setEnabled(otherMembersCount > 0);
        if (otherMembersCount <= 0) {
            btnTransfer.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.owner_leave.hover.no_other_members"));
        }
        addButton(btnTransfer, (btn, mouse) -> finishAction(LeaveHologramGroupPayload.ACTION_TRANSFER_RANDOM));

        // Button 3: Cancel
        String cancelLabel = StringUtils.translate("gui.cancel");
        ButtonGeneric btnCancel = new ButtonGeneric(btnStartX + btnDelW + spacing + btnTrW + spacing, btnY, btnCancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> closeGui(true));
    }

    private void finishAction(int action) {
        if (this.group != null) {
            ClientHologramGroupManager.getInstance().leaveGroup(this.group.getId(), action);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "schematic_synchronizer.message.left_group", this.group.getName());
        }
        returnToGroups();
    }

    private void returnToGroups() {
        Screen p = this.getParent();
        while (p instanceof GuiManageHologramGroup || p instanceof GuiManageGroupMembers || p instanceof GuiManageGroupSettings) {
            p = ((GuiBase) p).getParent();
        }
        if (p != null) {
            GuiBase.openGui(p);
        } else {
            GuiBase.openGui(new GuiHologramGroups());
        }
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 320;
        int dialogH = 110;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFF505050);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 320;
        int dialogH = 110;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String titleStr = "§f§l" + StringUtils.translate("schematic_synchronizer.gui.owner_leave.title");
        this.drawString(ctx, titleStr, x + 14, y + 12, 0xFFFFFFFF);

        String groupName = (this.group != null) ? this.group.getName() : "";
        String desc1 = StringUtils.translate("schematic_synchronizer.gui.owner_leave.desc1", groupName);
        String desc2 = StringUtils.translate("schematic_synchronizer.gui.owner_leave.desc2");

        this.drawString(ctx, "§7" + desc1, x + 14, y + 32, 0xFFAAAAAA);
        this.drawString(ctx, "§8" + desc2, x + 14, y + 46, 0xFF888888);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
