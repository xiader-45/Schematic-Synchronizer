package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;

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

        int dialogW = 340;
        int dialogH = 140;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        int btnY = y + dialogH - 32;

        // Button 1: Delete Group
        String delLabel = StringUtils.translate("schematic_synchronizer.gui.owner_leave.delete_group");
        int delW = 120;
        ButtonGeneric btnDelete = new ButtonGeneric(x + 12, btnY, delW, 20, delLabel);
        addButton(btnDelete, (btn, mouse) -> {
            ClientHologramGroupManager.getInstance().leaveGroup(this.group.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
            closeGui(true);
        });

        // Button 2: Transfer Random & Leave
        String trLabel = StringUtils.translate("schematic_synchronizer.gui.owner_leave.transfer_random");
        int trW = 130;
        ButtonGeneric btnTransfer = new ButtonGeneric(x + 12 + delW + 6, btnY, trW, 20, trLabel);
        addButton(btnTransfer, (btn, mouse) -> {
            ClientHologramGroupManager.getInstance().leaveGroup(this.group.getId(), LeaveHologramGroupPayload.ACTION_TRANSFER_RANDOM);
            closeGui(true);
        });

        // Button 3: Cancel
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = 60;
        ButtonGeneric btnCancel = new ButtonGeneric(x + dialogW - cancelW - 12, btnY, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> closeGui(true));
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 340;
        int dialogH = 140;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 340;
        int dialogH = 140;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.owner_leave.title");
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 12, 0xFFFFAA00);

        String groupName = (this.group != null) ? this.group.getName() : "";
        String desc1 = StringUtils.translate("schematic_synchronizer.gui.owner_leave.desc1", groupName);
        String desc2 = StringUtils.translate("schematic_synchronizer.gui.owner_leave.desc2");

        this.drawString(ctx, "§f" + desc1, x + 14, y + 36, 0xFFFFFFFF);
        this.drawString(ctx, "§7" + desc2, x + 14, y + 52, 0xFFAAAAAA);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
