package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.SchematicSynchronizer;
import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GuiSelectGroupForPlacement extends GuiBase {
    private static final Identifier ICON_GROUP_1 = SchematicSynchronizer.id("textures/gui/group_icon_1.png");
    private static final int ITEMS_PER_PAGE = 5;

    private final SchematicPlacement placement;
    private int page = 0;
    private long lastGroupsVersion = -1;

    public GuiSelectGroupForPlacement(Screen parent, SchematicPlacement placement) {
        this.setParent(parent);
        this.placement = placement;
        this.title = StringUtils.translate("schematic_synchronizer.gui.select_group.title",
                placement != null ? placement.getName() : "");
    }

    private List<HologramGroupData> getAllGroups() {
        return new ArrayList<>(ClientHologramGroupManager.getInstance().getGroups());
    }

    private boolean isPlacementAlreadyInGroup(HologramGroupData group) {
        if (group == null || this.placement == null) return false;
        for (GroupPlacementData gpd : group.getPlacements()) {
            if (gpd.getName().equals(this.placement.getName())
                    && gpd.getOrigin().equals(this.placement.getOrigin())) {
                return true;
            }
        }
        return false;
    }

    private boolean canPlayerAddToGroup(HologramGroupData group) {
        if (group == null) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);
        if (myUuid == null && myName == null) return false;
        if (group.isOwner(myUuid, myName)) return true;
        if (ClientHologramGroupManager.getInstance().canOpManage()) return true;

        String perm = group.getMemberPermission(myUuid, myName);
        return HologramGroupData.PERM_ALL.equals(perm)
                || HologramGroupData.PERM_ADD.equals(perm);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.createButtons();
    }

    private void createButtons() {
        this.clearButtons();

        int dialogW = 340;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        List<HologramGroupData> groups = getAllGroups();
        int maxPages = Math.max(1, (int) Math.ceil((double) groups.size() / ITEMS_PER_PAGE));
        if (this.page >= maxPages) {
            this.page = maxPages - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }

        int itemY = y + 42;
        int startIndex = this.page * ITEMS_PER_PAGE;
        int endIndex = Math.min(groups.size(), startIndex + ITEMS_PER_PAGE);

        for (int i = startIndex; i < endIndex; i++) {
            HologramGroupData group = groups.get(i);
            boolean alreadyIn = isPlacementAlreadyInGroup(group);
            boolean canAdd = canPlayerAddToGroup(group);

            String btnLabel;
            if (alreadyIn) {
                btnLabel = StringUtils.translate("schematic_synchronizer.gui.select_group.already_in");
            } else if (!canAdd) {
                btnLabel = StringUtils.translate("schematic_synchronizer.gui.select_group.no_perm");
            } else {
                btnLabel = StringUtils.translate("schematic_synchronizer.gui.select_group.select_btn");
            }

            int btnW = this.getStringWidth(btnLabel) + 16;
            int btnX = x + dialogW - btnW - 14;

            ButtonGeneric btnSelect = new ButtonGeneric(btnX, itemY + 4, btnW, 20, btnLabel);
            btnSelect.setEnabled(!alreadyIn && canAdd);
            if (alreadyIn) {
                btnSelect.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.select_group.hover.already_in"));
            } else if (!canAdd) {
                btnSelect.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.select_group.hover.no_perm"));
            }
            addButton(btnSelect, (btn, mouse) -> {
                addPlacementToGroup(group);
                GuiBase.openGui(new GuiManageHologramGroup(this.getParent(), group));
            });

            itemY += 28;
        }

        // Pagination buttons if needed
        if (maxPages > 1) {
            int pageY = y + dialogH - 56;
            ButtonGeneric btnPrev = new ButtonGeneric(x + 14, pageY, 24, 18, "<");
            btnPrev.setEnabled(this.page > 0);
            addButton(btnPrev, (btn, mouse) -> {
                this.page--;
                this.createButtons();
            });

            ButtonGeneric btnNext = new ButtonGeneric(x + 42, pageY, 24, 18, ">");
            btnNext.setEnabled(this.page < maxPages - 1);
            addButton(btnNext, (btn, mouse) -> {
                this.page++;
                this.createButtons();
            });
        }

        // Bottom action buttons
        int btnY = y + dialogH - 28;

        // Button 1: Create New Group
        String createLabel = StringUtils.translate("schematic_synchronizer.gui.select_group.create_new");
        int createW = this.getStringWidth(createLabel) + 16;
        ButtonGeneric btnCreate = new ButtonGeneric(x + 14, btnY, createW, 20, createLabel);
        addButton(btnCreate, (btn, mouse) -> {
            GuiBase.openGui(new GuiCreateHologramGroup(this.getParent(), this.placement));
        });

        // Button 2: Cancel / Back
        String cancelLabel = StringUtils.translate("gui.cancel");
        int cancelW = 60;
        ButtonGeneric btnCancel = new ButtonGeneric(x + dialogW - cancelW - 14, btnY, cancelW, 20, cancelLabel);
        addButton(btnCancel, (btn, mouse) -> {
            if (this.getParent() != null) {
                GuiBase.openGui(this.getParent());
            } else {
                this.closeGui(true);
            }
        });
    }

    private void addPlacementToGroup(HologramGroupData group) {
        if (group == null || this.placement == null) return;
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);

        String id = group.getId() + "_p" + System.currentTimeMillis();
        String name = this.placement.getName();
        String schematicId = ClientSchematicManager.getInstance().getSchematicIdForPlacement(this.placement);
        if (schematicId == null || schematicId.isEmpty()) {
            schematicId = name;
        }

        GroupPlacementData gpd = new GroupPlacementData(
                id,
                name,
                schematicId,
                this.placement.getOrigin(),
                this.placement.getRotation().name(),
                this.placement.getMirror().name(),
                this.placement.isLocked(),
                myUuid,
                this.placement.isEnabled()
        );

        ClientHologramGroupManager.getInstance().addPlacementsToGroup(group.getId(), Collections.singletonList(gpd));
    }

    @Override
    public void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 340;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        long ver = 0;
        for (HologramGroupData g : ClientHologramGroupManager.getInstance().getGroups()) {
            ver = ver * 31 + g.getLastModified() + g.hashCode();
        }
        if (this.lastGroupsVersion != ver) {
            this.lastGroupsVersion = ver;
            this.createButtons();
        }

        int dialogW = 340;
        int dialogH = 220;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.select_group.title",
                this.placement != null ? this.placement.getName() : "");
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 10, 0xFFFFAA00);

        String subtitle = StringUtils.translate("schematic_synchronizer.gui.select_group.subtitle");
        this.drawString(ctx, "§7" + subtitle, x + 14, y + 26, 0xFFAAAAAA);

        List<HologramGroupData> groups = getAllGroups();

        if (groups.isEmpty()) {
            String empty = StringUtils.translate("schematic_synchronizer.gui.select_group.no_groups");
            this.drawString(ctx, "§8" + empty, x + 14, y + 60, 0xFF888888);
        } else {
            int itemY = y + 42;
            int startIndex = this.page * ITEMS_PER_PAGE;
            int endIndex = Math.min(groups.size(), startIndex + ITEMS_PER_PAGE);

            for (int i = startIndex; i < endIndex; i++) {
                HologramGroupData group = groups.get(i);
                boolean alreadyIn = isPlacementAlreadyInGroup(group);

                RenderUtils.drawRect(ctx, x + 12, itemY, dialogW - 24, 26, 0x20FFFFFF);
                RenderUtils.drawOutline(ctx, x + 12, itemY, dialogW - 24, 26, 0x30FFFFFF);

                int iconX = x + 16;
                int iconY = itemY + 7;
                ctx.blit(RenderPipelines.GUI_TEXTURED, ICON_GROUP_1, iconX, iconY, 0.0f, 0.0f, 12, 12, 12, 12);

                String gName = (group.getName() != null && !group.getName().isEmpty()) ? group.getName() : group.getId();
                this.drawString(ctx, "§e" + gName, x + 32, itemY + 4, 0xFFFFFFFF);

                int pCount = group.getPlacements().size();
                int mCount = group.getMembers().size();
                String infoLine = "§7" + pCount + " " + StringUtils.translate("schematic_synchronizer.gui.group.placements_count")
                        + " §8| §7" + mCount + " " + StringUtils.translate("schematic_synchronizer.gui.group.members_count");
                this.drawString(ctx, infoLine, x + 32, itemY + 14, 0xFFAAAAAA);

                itemY += 28;
            }

            int maxPages = Math.max(1, (int) Math.ceil((double) groups.size() / ITEMS_PER_PAGE));
            if (maxPages > 1) {
                int pageY = y + dialogH - 52;
                String pageStr = "§7" + (this.page + 1) + " / " + maxPages;
                int pw = this.getStringWidth(pageStr);
                this.drawString(ctx, pageStr, x + (dialogW - pw) / 2, pageY, 0xFFAAAAAA);
            }
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
