package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;

public class GuiManageGroupSettings extends GuiBase {
    private final HologramGroupData group;
    private ButtonGeneric btnCustomVis;
    private ButtonGeneric btnRemoveOnLeave;

    public GuiManageGroupSettings(Screen parent, HologramGroupData group) {
        this.setParent(parent);
        this.group = group;
        this.title = StringUtils.translate("schematic_synchronizer.gui.manage_group.title_named", group != null ? group.getName() : "");
    }

    public HologramGroupData getGroup() {
        if (this.group == null) return null;
        HologramGroupData latest = ClientHologramGroupManager.getInstance().getGroupById(this.group.getId());
        return latest != null ? latest : this.group;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearButtons();
        this.createButtons();
    }

    private String getVisibilityModeText(boolean individual) {
        return individual
                ? StringUtils.translate("schematic_synchronizer.gui.settings.custom_visibility.mode.individual")
                : StringUtils.translate("schematic_synchronizer.gui.settings.custom_visibility.mode.synchronized");
    }

    private String getRemoveModeText(boolean remove) {
        return remove
                ? StringUtils.translate("schematic_synchronizer.gui.settings.remove_on_leave.mode.delete")
                : StringUtils.translate("schematic_synchronizer.gui.settings.remove_on_leave.mode.keep");
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

        // Tab 2: Members
        String tabMembersLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_members", memberCount);
        int tabMW = this.getStringWidth(tabMembersLabel) + 16;
        ButtonGeneric btnTabM = new ButtonGeneric(x, tabY, tabMW, 20, tabMembersLabel);
        addButton(btnTabM, (btn, mouse) -> {
            GuiBase.openGui(new GuiManageGroupMembers(this.getParent(), this.getGroup()));
        });
        x += tabMW + 4;

        // Tab 3: Settings (Active)
        String tabSettingsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_settings");
        int tabSW = this.getStringWidth(tabSettingsLabel) + 16;
        ButtonGeneric btnTabS = new ButtonGeneric(x, tabY, tabSW, 20, tabSettingsLabel);
        btnTabS.setEnabled(false);
        addButton(btnTabS, (btn, mouse) -> {});

        // Layout calculations for settings entries
        int boxX = 10;
        int boxY = 48;
        int boxW = this.width - 20;
        int entryX = boxX + 6;
        int entryW = boxW - 12;
        int entryH = 34;

        int entryY1 = boxY + 6;
        int entryY2 = entryY1 + entryH + 4;

        int btnW = 140;
        int btnH = 20;
        int btnX = entryX + entryW - btnW - 8;
        int btnY1 = entryY1 + (entryH - btnH) / 2;
        int btnY2 = entryY2 + (entryH - btnH) / 2;

        // Setting 1: Custom Placement Visibility
        boolean visVal = ClientHologramGroupManager.getInstance().isCustomVisibilityEnabled(g != null ? g.getId() : "");
        this.btnCustomVis = new ButtonGeneric(btnX, btnY1, btnW, btnH, getVisibilityModeText(visVal));
        addButton(this.btnCustomVis, (btn, mouse) -> {
            HologramGroupData curGroup = getGroup();
            String gid = curGroup != null ? curGroup.getId() : "";
            boolean now = !ClientHologramGroupManager.getInstance().isCustomVisibilityEnabled(gid);
            ClientHologramGroupManager.getInstance().setCustomVisibilityEnabled(gid, now);
            this.btnCustomVis.setDisplayString(getVisibilityModeText(now));
        });

        // Setting 2: Delete Placements on Leave/Delete
        boolean leaveVal = ClientHologramGroupManager.getInstance().isRemovePlacementsOnLeaveEnabled();
        this.btnRemoveOnLeave = new ButtonGeneric(btnX, btnY2, btnW, btnH, getRemoveModeText(leaveVal));
        addButton(this.btnRemoveOnLeave, (btn, mouse) -> {
            boolean now = !ClientHologramGroupManager.getInstance().isRemovePlacementsOnLeaveEnabled();
            ClientHologramGroupManager.getInstance().setRemovePlacementsOnLeaveEnabled(now);
            this.btnRemoveOnLeave.setDisplayString(getRemoveModeText(now));
        });

        // Bottom Bar: Back Button
        String backLabel = StringUtils.translate("gui.back");
        int backW = this.getStringWidth(backLabel) + 20;
        int backX = this.width - backW - 10;
        int backY = this.height - 24;
        ButtonGeneric btnBack = new ButtonGeneric(backX, backY, backW, 20, backLabel);
        addButton(btnBack, (btn, mouse) -> {
            if (this.getParent() != null) {
                GuiBase.openGui(this.getParent());
            } else {
                this.closeGui(true);
            }
        });
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        HologramGroupData g = getGroup();
        String dimStr = (g != null && g.getDimension() != null && !g.getDimension().isEmpty()) ? "  §8|  §7" + g.getDimension() : "";
        this.drawString(ctx, this.getTitleString() + dimStr, 20, 10, -1);
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int boxX = 10;
        int boxY = 48;
        int boxW = this.width - 20;
        int boxH = this.height - 76;
        RenderUtils.drawOutlinedBox(ctx, boxX, boxY, boxW, boxH, 0x40000000, 0xFF404040);

        int entryX = boxX + 6;
        int entryW = boxW - 12;
        int entryH = 34;
        int entryY1 = boxY + 6;
        int entryY2 = entryY1 + entryH + 4;

        // Entry 1 card background
        RenderUtils.drawRect(ctx, entryX, entryY1, entryW, entryH, 0x20FFFFFF);
        RenderUtils.drawOutline(ctx, entryX, entryY1, entryW, entryH, 0x30FFFFFF);

        // Entry 2 card background
        RenderUtils.drawRect(ctx, entryX, entryY2, entryW, entryH, 0x20FFFFFF);
        RenderUtils.drawOutline(ctx, entryX, entryY2, entryW, entryH, 0x30FFFFFF);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        HologramGroupData g = getGroup();
        if (g == null) {
            this.closeGui(true);
            return;
        }

        int boxX = 10;
        int boxY = 48;
        int entryX = boxX + 6;
        int entryH = 34;
        int entryY1 = boxY + 6;
        int entryY2 = entryY1 + entryH + 4;

        // Entry 1 text
        String setting1Name = StringUtils.translate("schematic_synchronizer.gui.settings.custom_visibility.name");
        String setting1Desc = StringUtils.translate("schematic_synchronizer.gui.settings.custom_visibility.desc");
        this.drawString(ctx, "§f" + setting1Name, entryX + 8, entryY1 + 6, 0xFFFFFFFF);
        this.drawString(ctx, "§7" + setting1Desc, entryX + 8, entryY1 + 18, 0xFFAAAAAA);

        // Entry 2 text
        String setting2Name = StringUtils.translate("schematic_synchronizer.gui.settings.remove_on_leave.name");
        String setting2Desc = StringUtils.translate("schematic_synchronizer.gui.settings.remove_on_leave.desc");
        this.drawString(ctx, "§f" + setting2Name, entryX + 8, entryY2 + 6, 0xFFFFFFFF);
        this.drawString(ctx, "§7" + setting2Desc, entryX + 8, entryY2 + 18, 0xFFAAAAAA);

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
