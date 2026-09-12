package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
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

        int dialogW = 380;
        int maxMembers = (this.group != null) ? this.group.getMembers().size() : 0;
        int dialogH = Math.max(160, Math.min(300, 60 + maxMembers * 26 + 40));

        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        if (this.group != null) {
            int memberY = y + 40;
            List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

            for (Map.Entry<UUID, String> entry : memberList) {
                UUID memberUuid = entry.getKey();
                String memberName = entry.getValue();
                boolean isOwner = this.group.isOwner(memberUuid);

                if (!isOwner) {
                    // Transfer ownership button
                    String trLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.transfer");
                    int trW = 90;
                    ButtonGeneric btnTr = new ButtonGeneric(x + dialogW - 170, memberY - 2, trW, 18, trLabel);
                    addButton(btnTr, (btn, mouse) -> {
                        ClientHologramGroupManager.getInstance().transferOwnership(this.group.getId(), memberUuid);
                        closeGui(true);
                    });

                    // Kick button
                    String kickLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.kick");
                    int kickW = 70;
                    ButtonGeneric btnKick = new ButtonGeneric(x + dialogW - 74, memberY - 2, kickW, 18, kickLabel);
                    addButton(btnKick, (btn, mouse) -> {
                        ClientHologramGroupManager.getInstance().kickMember(this.group.getId(), memberUuid);
                        this.group.removeMember(memberUuid);
                        this.createButtons();
                    });
                }
                memberY += 24;
            }
        }

        // Back button
        String backLabel = StringUtils.translate("gui.back");
        int backW = 80;
        ButtonGeneric btnBack = new ButtonGeneric(x + (dialogW - backW) / 2, y + dialogH - 28, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> closeGui(true));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 380;
        int maxMembers = (this.group != null) ? this.group.getMembers().size() : 0;
        int dialogH = Math.max(160, Math.min(300, 60 + maxMembers * 26 + 40));

        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);

        String grpName = (this.group != null) ? this.group.getName() : "";
        String titleStr = "\u00a76\u00a7l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.title_with_name", grpName);
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 12, 0xFFFFAA00);

        if (this.group != null) {
            int memberY = y + 42;
            List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

            for (Map.Entry<UUID, String> entry : memberList) {
                UUID memberUuid = entry.getKey();
                String memberName = entry.getValue();
                boolean isOwner = this.group.isOwner(memberUuid);

                String text = isOwner
                        ? "\u00a76\u00a7l" + memberName + " \u00a7e[" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + "]"
                        : "\u00a7f" + memberName;
                this.drawString(ctx, text, x + 16, memberY, 0xFFFFFFFF);
                memberY += 24;
            }
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
