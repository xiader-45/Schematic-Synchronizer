package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public class WidgetHologramGroupEntry extends WidgetListEntryBase<HologramGroupData> {
    private static final Identifier ICON_GROUP_1 = SchematicSynchronizer.id("textures/gui/group_icon_1.png");

    private static long lastClickTime = 0;
    private static String lastClickedGroupId = null;

    private final boolean isOdd;
    private final WidgetListHologramGroups parentList;
    private ButtonGeneric btnJoinLeave;

    public WidgetHologramGroupEntry(int x, int y, int width, int height,
                                    HologramGroupData entry, int listIndex,
                                    WidgetListHologramGroups parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;

        if (entry != null) {
            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
            boolean isOwner = (myUuid != null && entry.isOwner(myUuid));
            if (!isOwner && mc.player != null && entry.getOwnerName() != null && entry.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) {
                isOwner = true;
            }
            boolean isMember = (myUuid != null && entry.isMember(myUuid));

            int btnH = 20;
            int btnW = 60;
            int btnX = x + width - btnW - 4;
            int btnY = y + (height - btnH) / 2;

            String label;
            String hover;
            if (isOwner) {
                label = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group_short");
                hover = StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group");
            } else if (isMember) {
                label = StringUtils.translate("schematic_synchronizer.gui.button.leave_group_short");
                hover = StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.leave_group");
            } else {
                label = StringUtils.translate("schematic_synchronizer.gui.button.join_group_short");
                hover = StringUtils.translate("schematic_synchronizer.gui.button.join_group");
            }

            this.btnJoinLeave = new ButtonGeneric(btnX, btnY, btnW, btnH, label);
            this.btnJoinLeave.setHoverStrings(hover);
            this.addButton(this.btnJoinLeave, (btn, mouse) -> {
                Minecraft m = Minecraft.getInstance();
                UUID u = (m.player != null) ? m.player.getUUID() : (m.getUser() != null ? m.getUser().getProfileId() : null);
                boolean owner = (u != null && this.entry.isOwner(u));
                if (!owner && m.player != null && this.entry.getOwnerName() != null && this.entry.getOwnerName().equalsIgnoreCase(m.player.getName().getString())) {
                    owner = true;
                }
                boolean member = (u != null && this.entry.isMember(u));

                if (owner) {
                    GuiBase.openGui(new GuiConfirmOwnerLeave(this.parentList.getParentGui(), this.entry));
                } else if (member) {
                    ClientHologramGroupManager.getInstance().leaveGroup(this.entry.getId(), LeaveHologramGroupPayload.ACTION_TRANSFER_RANDOM);
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "schematic_synchronizer.message.left_group", this.entry.getName());
                    this.parentList.getParentGui().refreshList();
                } else {
                    ClientHologramGroupManager.getInstance().joinGroup(this.entry.getId());
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "schematic_synchronizer.message.joined_group", this.entry.getName());
                    this.parentList.getParentGui().refreshList();
                }
            });
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent event, boolean isDouble) {
        if (event.input() != 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        boolean isDoubleClick = isDouble || (lastClickedGroupId != null && this.entry != null && lastClickedGroupId.equals(this.entry.getId()) && (now - lastClickTime) < 600);
        lastClickTime = now;
        lastClickedGroupId = (this.entry != null) ? this.entry.getId() : null;

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

        boolean isOwner = (myUuid != null && this.entry != null && this.entry.isOwner(myUuid));
        if (!isOwner && mc.player != null && this.entry != null && this.entry.getOwnerName() != null && this.entry.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) {
            isOwner = true;
        }
        boolean isMember = (myUuid != null && this.entry != null && this.entry.isMember(myUuid));
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();

        this.parentList.setLastSelectedEntry(this.entry, this.listIndex);
        this.parentList.getParentGui().onSelectionChange(this.entry);

        if (isDoubleClick && this.entry != null) {
            lastClickTime = 0;
            lastClickedGroupId = null;
            if (isMember || isOwner || isOp) {
                GuiBase.openGui(new GuiManageHologramGroup(this.parentList.getParentGui(), this.entry));
            }
            return true;
        }
        return true;
    }

    private String getPlacementsCountText(int count) {
        return StringUtils.translate("schematic_synchronizer.gui.group.placements_count", count);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        boolean isSelected = selected || (this.entry != null && this.parentList.getLastSelectedEntry() != null && this.entry.getId().equals(this.parentList.getLastSelectedEntry().getId()));

        if (isSelected) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x70FFFFFF);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xEEEEEEEE);
        } else if (this.isMouseOver(mouseX, mouseY)) {
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xEEEEEEEE);
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x50FFFFFF);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        }

        if (this.entry != null) {
            int iconX = this.x + 5;
            int iconY = this.y + (this.height - 11) / 2;
            ctx.blit(RenderPipelines.GUI_TEXTURED, ICON_GROUP_1, iconX, iconY, 0.0f, 0.0f, 11, 11, 11, 11);

            int textX = this.x + 22;
            int textY = this.y + 3;

            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

            boolean isOwner = (myUuid != null && this.entry.isOwner(myUuid));
            if (!isOwner && mc.player != null && this.entry.getOwnerName() != null && this.entry.getOwnerName().equalsIgnoreCase(mc.player.getName().getString())) {
                isOwner = true;
            }
            boolean isMember = (myUuid != null && this.entry.isMember(myUuid));

            if (this.btnJoinLeave != null) {
                String label;
                String hover;
                if (isOwner) {
                    label = StringUtils.translate("schematic_synchronizer.gui.manage_group.delete_group_short");
                    hover = StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_group");
                } else if (isMember) {
                    label = StringUtils.translate("schematic_synchronizer.gui.button.leave_group_short");
                    hover = StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.leave_group");
                } else {
                    label = StringUtils.translate("schematic_synchronizer.gui.button.join_group_short");
                    hover = StringUtils.translate("schematic_synchronizer.gui.button.join_group");
                }
                this.btnJoinLeave.setDisplayString(label);
                this.btnJoinLeave.setHoverStrings(hover);
            }

            // Line 1: Group Name + status indicator
            String nameLine = "§e" + this.entry.getName();
            if (isOwner) {
                nameLine += " §6★";
            } else if (isMember) {
                nameLine += " §a●";
            }
            this.drawString(ctx, textX, textY, 0xFFFFFFFF, nameLine);

            // Line 2: Placements count, members count, owner
            int plCount = this.entry.getPlacements().size();
            int mCount = this.entry.getMembers().size();
            String ownerName = this.entry.getOwnerName() != null ? this.entry.getOwnerName() : "Unknown";

            String detail = "§f" + getPlacementsCountText(plCount)
                    + "  §7|  §f" + StringUtils.translate("schematic_synchronizer.gui.group.members_count", mCount)
                    + "  §7|  §f" + StringUtils.translate("schematic_synchronizer.gui.group.owner_prefix", ownerName);
            this.drawString(ctx, textX, textY + 11, 0xFFFFFFFF, detail);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean isOdd) {
        super.postRenderHovered(ctx, mouseX, mouseY, isOdd);

        if (this.btnJoinLeave != null && this.btnJoinLeave.hasHoverText()) {
            if (mouseX >= this.btnJoinLeave.getX() && mouseX < this.btnJoinLeave.getX() + this.btnJoinLeave.getWidth() &&
                mouseY >= this.btnJoinLeave.getY() && mouseY < this.btnJoinLeave.getY() + this.btnJoinLeave.getHeight()) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, this.btnJoinLeave.getHoverStrings());
            }
        }
    }
}
