package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;

public class WidgetGroupMemberEntry extends WidgetListEntryBase<Map.Entry<UUID, String>> {
    private final boolean isOdd;
    private final WidgetListGroupMembers parentList;

    public WidgetGroupMemberEntry(int x, int y, int width, int height,
                                  Map.Entry<UUID, String> entry, int listIndex,
                                  WidgetListGroupMembers parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;

        HologramGroupData group = parentList.getParentGui().getGroup();
        boolean canManage = parentList.getParentGui().canManage();
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;

        if (canManage && group != null && entry != null) {
            UUID memberUuid = entry.getKey();
            boolean memberIsOwner = group.isOwner(memberUuid);

            int btnH = 20;
            int rightX = x + width - 4;

            if (!memberIsOwner || isOp) {
                if (!memberUuid.equals(myUuid) || isOp) {
                    String kickLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.kick");
                    int kickW = 75;
                    rightX -= kickW;
                    ButtonGeneric btnKick = new ButtonGeneric(rightX, y + (height - btnH) / 2, kickW, btnH, kickLabel);
                    this.addButton(btnKick, (btn, mouse) -> {
                        ClientHologramGroupManager.getInstance().kickMember(group.getId(), memberUuid);
                        group.removeMember(memberUuid);
                        parentList.getParentGui().refreshList();
                    });
                    rightX -= 4;
                }

                if (!memberIsOwner) {
                    String trLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.transfer");
                    int trW = 105;
                    rightX -= trW;
                    ButtonGeneric btnTr = new ButtonGeneric(rightX, y + (height - btnH) / 2, trW, btnH, trLabel);
                    this.addButton(btnTr, (btn, mouse) -> {
                        ClientHologramGroupManager.getInstance().transferOwnership(group.getId(), memberUuid);
                        parentList.getParentGui().closeScreen();
                    });
                }
            }
        }
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (selected) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x60FFAA00);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFFFFAA00);
        } else if (this.isMouseOver(mouseX, mouseY)) {
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xEEEEEEEE);
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        }

        if (this.entry == null) return;

        HologramGroupData group = this.parentList.getParentGui().getGroup();
        UUID memberUuid = this.entry.getKey();
        String memberName = this.entry.getValue();
        boolean isOwner = (group != null && group.isOwner(memberUuid));

        int textX = this.x + 8;
        int textY = this.y + 7;

        if (isOwner) {
            String text = "§6★ §l" + memberName + "  §e[" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + "]";
            this.drawString(ctx, textX, textY, 0xFFFFAA00, text);
        } else {
            String text = "§f" + memberName + "  §7[" + StringUtils.translate("schematic_synchronizer.gui.group.status.member") + "]";
            this.drawString(ctx, textX, textY, 0xFFFFFFFF, text);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }
}
