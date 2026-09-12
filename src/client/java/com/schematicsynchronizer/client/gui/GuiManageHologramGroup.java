package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManageHologramGroup extends GuiBase {
    private final HologramGroupData group;

    public GuiManageHologramGroup(Screen parent, HologramGroupData group) {
        this.setParent(parent);
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.manage_group.title");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.createButtons();
    }

    private void createButtons() {
        this.clearButtons();

        int dialogW = 420;
        int maxMembers = (this.group != null) ? this.group.getMembers().size() : 0;
        int dialogH = Math.max(170, Math.min(320, 70 + maxMembers * 26 + 45));

        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean isOwner = (this.group != null && myUuid != null && this.group.isOwner(myUuid));
        boolean canManage = isOwner || isOp;

        // OP button: "Забрать права себе (OP)"
        if (isOp && !isOwner && this.group != null && myUuid != null) {
            String takeLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.take_ownership_op");
            int takeW = this.getStringWidth(takeLabel) + 14;
            ButtonGeneric btnTake = new ButtonGeneric(x + dialogW - takeW - 14, y + 10, takeW, 20, takeLabel);
            addButton(btnTake, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().transferOwnership(this.group.getId(), myUuid);
                closeGui(true);
            });
        }

        if (this.group != null && canManage) {
            int memberY = y + 42;
            List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

            for (Map.Entry<UUID, String> entry : memberList) {
                UUID memberUuid = entry.getKey();
                boolean memberIsOwner = this.group.isOwner(memberUuid);

                if (!memberIsOwner || isOp) {
                    if (!memberIsOwner) {
                        // Transfer ownership button
                        String trLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.transfer");
                        int trW = 90;
                        ButtonGeneric btnTr = new ButtonGeneric(x + dialogW - 170, memberY - 2, trW, 18, trLabel);
                        addButton(btnTr, (btn, mouse) -> {
                            ClientHologramGroupManager.getInstance().transferOwnership(this.group.getId(), memberUuid);
                            closeGui(true);
                        });
                    }

                    // Kick button (can kick anyone if OP, except cannot kick self if owner)
                    if (!memberUuid.equals(myUuid) || isOp) {
                        String kickLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.kick");
                        int kickW = 70;
                        ButtonGeneric btnKick = new ButtonGeneric(x + dialogW - 74, memberY - 2, kickW, 18, kickLabel);
                        addButton(btnKick, (btn, mouse) -> {
                            ClientHologramGroupManager.getInstance().kickMember(this.group.getId(), memberUuid);
                            this.group.removeMember(memberUuid);
                            this.createButtons();
                        });
                    }
                }
                memberY += 24;
            }
        }

        // Bottom buttons: Delete Group & Back
        int bottomY = y + dialogH - 28;

        if (canManage && this.group != null) {
            String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group");
            int delW = this.getStringWidth(delLabel) + 20;
            ButtonGeneric btnDel = new ButtonGeneric(x + 14, bottomY, delW, 20, delLabel);
            addButton(btnDel, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().leaveGroup(this.group.getId(), LeaveHologramGroupPayload.ACTION_DELETE);
                closeGui(true);
            });
        }

        String backLabel = StringUtils.translate("gui.back");
        int backW = 80;
        ButtonGeneric btnBack = new ButtonGeneric(x + dialogW - backW - 14, bottomY, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 420;
        int maxMembers = (this.group != null) ? this.group.getMembers().size() : 0;
        int dialogH = Math.max(170, Math.min(320, 70 + maxMembers * 26 + 45));

        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);

        String grpName = (this.group != null) ? this.group.getName() : "";
        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.title_with_name", grpName);
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 14, 0xFFFFAA00);

        if (this.group != null) {
            int memberY = y + 44;
            List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

            for (Map.Entry<UUID, String> entry : memberList) {
                UUID memberUuid = entry.getKey();
                String memberName = entry.getValue();
                boolean isOwner = this.group.isOwner(memberUuid);

                String text = isOwner
                        ? "§6§l" + memberName + " §e[" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + "]"
                        : "§f" + memberName;
                this.drawString(ctx, text, x + 16, memberY, 0xFFFFFFFF);
                memberY += 24;
            }
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
