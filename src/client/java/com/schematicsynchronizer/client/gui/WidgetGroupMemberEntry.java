package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.util.PlayerSkinHelper;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;

public class WidgetGroupMemberEntry extends WidgetListEntryBase<Map.Entry<UUID, String>> {
    private final boolean isOdd;
    private final WidgetListGroupMembers parentList;
    private ButtonGeneric btnKick;
    private ButtonGeneric btnTr;
    private ButtonGeneric btnPerm;

    public WidgetGroupMemberEntry(int x, int y, int width, int height,
                                  Map.Entry<UUID, String> entry, int listIndex,
                                  WidgetListGroupMembers parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;

        if (entry != null) {
            UUID memberUuid = entry.getKey();
            String memberName = entry.getValue();
            HologramGroupData group = parentList.getParentGui().getGroup();

            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
            String myName = (mc.player != null) ? mc.player.getName().getString() : null;

            boolean viewerIsOwner = (group != null && myUuid != null && group.isOwner(myUuid));
            if (!viewerIsOwner && mc.player != null && group != null && group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(myName)) {
                viewerIsOwner = true;
            }
            boolean viewerIsOp = ClientHologramGroupManager.getInstance().canOpManage();
            boolean viewerCanManage = viewerIsOwner || viewerIsOp;

            boolean memberIsOwner = (group != null && group.isOwner(memberUuid));
            if (!memberIsOwner && group != null && group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(memberName)) {
                memberIsOwner = true;
            }

            boolean isSelf = (myUuid != null && myUuid.equals(memberUuid))
                    || (myName != null && myName.equalsIgnoreCase(memberName));

            int btnH = 20;
            int rightX = x + width - 4;

            // 1. Kick button (if not owner and viewer is OP or owner)
            if (!memberIsOwner && !isSelf && viewerCanManage) {
                String kickLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.kick");
                int kickW = 70;
                rightX -= kickW;
                this.btnKick = new ButtonGeneric(rightX, y + (height - btnH) / 2, kickW, btnH, kickLabel);
                this.btnKick.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.kick_member"));
                this.addButton(this.btnKick, (btn, mouse) -> {
                    ClientHologramGroupManager.getInstance().kickMember(group.getId(), memberUuid);
                    parentList.getParentGui().refreshList();
                });
                rightX -= 4;
            }

            // 2. Transfer ownership button (if not owner and viewer can manage)
            if (!memberIsOwner && viewerCanManage) {
                String trLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.transfer");
                int trW = 105;
                rightX -= trW;
                this.btnTr = new ButtonGeneric(rightX, y + (height - btnH) / 2, trW, btnH, trLabel);
                this.btnTr.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.transfer_ownership"));
                this.addButton(this.btnTr, (btn, mouse) -> {
                    ClientHologramGroupManager.getInstance().transferOwnership(group.getId(), memberUuid);
                    GuiBase.openGui(parentList.getParentGui().getParent());
                });
                rightX -= 4;
            }

            // 3. Permissions button (for other members, not self and not owner)
            if (!isSelf && !memberIsOwner && viewerCanManage) {
                String currentPerm = group.getMemberPermission(memberUuid);
                String permName = getPermissionDisplayName(currentPerm);
                String btnText = StringUtils.translate("schematic_synchronizer.gui.manage_group.perm_btn", permName);
                int permW = 135;
                rightX -= permW;
                this.btnPerm = new ButtonGeneric(rightX, y + (height - btnH) / 2, permW, btnH, btnText);
                this.btnPerm.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.change_permission"));
                this.addButton(this.btnPerm, (btn, mouse) -> {
                    String nextPerm = getNextPermission(group.getMemberPermission(memberUuid));
                    ClientHologramGroupManager.getInstance().updateMemberPermission(group.getId(), memberUuid, nextPerm);
                    parentList.getParentGui().refreshList();
                });
            }
        }
    }

    private String getPermissionDisplayName(String perm) {
        if (HologramGroupData.PERM_ALL.equals(perm)) {
            return StringUtils.translate("schematic_synchronizer.gui.manage_group.perm.all");
        } else if (HologramGroupData.PERM_EDIT.equals(perm)) {
            return StringUtils.translate("schematic_synchronizer.gui.manage_group.perm.edit");
        } else if (HologramGroupData.PERM_ADD.equals(perm)) {
            return StringUtils.translate("schematic_synchronizer.gui.manage_group.perm.add");
        } else {
            return StringUtils.translate("schematic_synchronizer.gui.manage_group.perm.read");
        }
    }

    private String getNextPermission(String current) {
        if (HologramGroupData.PERM_ALL.equals(current)) {
            return HologramGroupData.PERM_EDIT;
        } else if (HologramGroupData.PERM_EDIT.equals(current)) {
            return HologramGroupData.PERM_ADD;
        } else if (HologramGroupData.PERM_ADD.equals(current)) {
            return HologramGroupData.PERM_READ;
        } else {
            return HologramGroupData.PERM_ALL;
        }
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent event) {
        return false;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        }

        if (this.entry == null) return;

        UUID memberUuid = this.entry.getKey();
        String memberName = this.entry.getValue();
        HologramGroupData group = this.parentList.getParentGui().getGroup();

        boolean isOwner = (group != null && group.isOwner(memberUuid));
        if (!isOwner && group != null && group.getOwnerName() != null && group.getOwnerName().equalsIgnoreCase(memberName)) {
            isOwner = true;
        }

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : null;
        boolean isSelf = (myUuid != null && myUuid.equals(memberUuid))
                || (myName != null && myName.equalsIgnoreCase(memberName));

        int headSize = 10;
        int headX = this.x + 5;
        int headY = this.y + (this.height - headSize) / 2;

        try {
            PlayerSkin skin = PlayerSkinHelper.getSkinForPlayer(memberUuid, memberName);
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(ctx, skin, headX, headY, headSize);
            }
        } catch (Throwable ignored) {
        }
        RenderUtils.drawOutline(ctx, headX - 1, headY - 1, headSize + 2, headSize + 2, 0xFF3573FF);

        int textX = headX + headSize + 6;
        int textY = this.y + (this.height - 8) / 2;

        String nameStr = "§f" + memberName;
        if (isSelf) {
            nameStr += " §a(" + StringUtils.translate("schematic_synchronizer.gui.group.member.you") + ")";
        }
        if (isOwner) {
            nameStr += " §6(" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + ")";
        } else if (group != null) {
            String perm = group.getMemberPermission(memberUuid);
            nameStr += " §8[" + getPermissionDisplayName(perm) + "§8]";
        }

        this.drawString(ctx, textX, textY, 0xFFFFFFFF, nameStr);

        super.render(ctx, mouseX, mouseY, selected);
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean isOdd) {
        super.postRenderHovered(ctx, mouseX, mouseY, isOdd);

        drawButtonHover(ctx, mouseX, mouseY, this.btnKick);
        drawButtonHover(ctx, mouseX, mouseY, this.btnTr);
        drawButtonHover(ctx, mouseX, mouseY, this.btnPerm);
    }

    private void drawButtonHover(GuiContext ctx, int mouseX, int mouseY, ButtonBase btn) {
        if (btn != null && btn.hasHoverText()) {
            if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth() &&
                mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, btn.getHoverStrings());
            }
        }
    }
}
