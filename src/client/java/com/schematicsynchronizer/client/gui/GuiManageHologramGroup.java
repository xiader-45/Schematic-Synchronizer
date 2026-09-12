package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import com.schematicsynchronizer.network.LeaveHologramGroupPayload;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManageHologramGroup extends GuiBase {
    public enum Tab {
        PLACEMENTS,
        MEMBERS
    }

    private final HologramGroupData group;
    private Tab activeTab = Tab.PLACEMENTS;
    private int scrollOffset = 0;

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

        int dialogW = 460;
        int dialogH = 280;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.getUser().getProfileId() : null;
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean isOwner = (this.group != null && myUuid != null && this.group.isOwner(myUuid));
        boolean canManage = isOwner || isOp;

        // Top Navigation Tabs
        int tabY = y + 26;
        int placeCount = (this.group != null) ? this.group.getPlacements().size() : 0;
        int memberCount = (this.group != null) ? this.group.getMembers().size() : 0;

        String tabPlacementsLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_placements", placeCount);
        int tabPW = this.getStringWidth(tabPlacementsLabel) + 16;
        ButtonGeneric btnTabP = new ButtonGeneric(x + 14, tabY, tabPW, 20, tabPlacementsLabel);
        btnTabP.setEnabled(this.activeTab != Tab.PLACEMENTS);
        addButton(btnTabP, (btn, mouse) -> {
            this.activeTab = Tab.PLACEMENTS;
            this.scrollOffset = 0;
            this.createButtons();
        });

        String tabMembersLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.tab_members", memberCount);
        int tabMW = this.getStringWidth(tabMembersLabel) + 16;
        ButtonGeneric btnTabM = new ButtonGeneric(x + 14 + tabPW + 4, tabY, tabMW, 20, tabMembersLabel);
        btnTabM.setEnabled(this.activeTab != Tab.MEMBERS);
        addButton(btnTabM, (btn, mouse) -> {
            this.activeTab = Tab.MEMBERS;
            this.scrollOffset = 0;
            this.createButtons();
        });

        // Tab Content
        if (this.activeTab == Tab.PLACEMENTS) {
            // Button: Add Placement
            if (canManage && this.group != null) {
                String addLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.add_placement");
                int addW = this.getStringWidth(addLabel) + 16;
                ButtonGeneric btnAddPlacement = new ButtonGeneric(x + dialogW - addW - 14, tabY, addW, 20, addLabel);
                addButton(btnAddPlacement, (btn, mouse) -> {
                    GuiBase.openGui(new GuiAddPlacementsToGroup(this, this.group));
                });
            }

            // List placements
            if (this.group != null) {
                List<GroupPlacementData> placements = this.group.getPlacements();
                int itemY = y + 56;
                int maxItems = 7;
                int start = Math.min(scrollOffset, Math.max(0, placements.size() - maxItems));

                for (int i = start; i < Math.min(placements.size(), start + maxItems); i++) {
                    GroupPlacementData p = placements.get(i);
                    if (canManage) {
                        String delLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.remove_placement");
                        int delW = this.getStringWidth(delLabel) + 12;
                        ButtonGeneric btnRemove = new ButtonGeneric(x + dialogW - delW - 14, itemY + 2, delW, 18, delLabel);
                        addButton(btnRemove, (btn, mouse) -> {
                            ClientHologramGroupManager.getInstance().removePlacementFromGroup(this.group.getId(), p.getId());
                            this.group.removePlacement(p.getId());
                            this.createButtons();
                        });
                    }
                    itemY += 24;
                }
            }
        } else if (this.activeTab == Tab.MEMBERS) {
            // OP: "Take Ownership" button
            if (isOp && !isOwner && this.group != null && myUuid != null) {
                String takeLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.take_ownership_op");
                int takeW = this.getStringWidth(takeLabel) + 14;
                ButtonGeneric btnTake = new ButtonGeneric(x + dialogW - takeW - 14, tabY, takeW, 20, takeLabel);
                addButton(btnTake, (btn, mouse) -> {
                    ClientHologramGroupManager.getInstance().transferOwnership(this.group.getId(), myUuid);
                    closeGui(true);
                });
            }

            if (this.group != null && canManage) {
                int itemY = y + 56;
                List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

                for (Map.Entry<UUID, String> entry : memberList) {
                    UUID memberUuid = entry.getKey();
                    boolean memberIsOwner = this.group.isOwner(memberUuid);

                    if (!memberIsOwner || isOp) {
                        if (!memberIsOwner) {
                            String trLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.transfer");
                            int trW = 90;
                            ButtonGeneric btnTr = new ButtonGeneric(x + dialogW - 170, itemY + 2, trW, 18, trLabel);
                            addButton(btnTr, (btn, mouse) -> {
                                ClientHologramGroupManager.getInstance().transferOwnership(this.group.getId(), memberUuid);
                                closeGui(true);
                            });
                        }

                        if (!memberUuid.equals(myUuid) || isOp) {
                            String kickLabel = StringUtils.translate("schematic_synchronizer.gui.manage_group.kick");
                            int kickW = 70;
                            ButtonGeneric btnKick = new ButtonGeneric(x + dialogW - 74, itemY + 2, kickW, 18, kickLabel);
                            addButton(btnKick, (btn, mouse) -> {
                                ClientHologramGroupManager.getInstance().kickMember(this.group.getId(), memberUuid);
                                this.group.removeMember(memberUuid);
                                this.createButtons();
                            });
                        }
                    }
                    itemY += 24;
                }
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
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogW = 460;
        int dialogH = 280;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, dialogW, dialogH, 0xF0101010, 0xFFFFAA00);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogW = 460;
        int dialogH = 280;
        int x = (this.width - dialogW) / 2;
        int y = (this.height - dialogH) / 2;

        String grpName = (this.group != null) ? this.group.getName() : "";
        String titleStr = "§6§l" + StringUtils.translate("schematic_synchronizer.gui.manage_group.title_with_name", grpName);
        this.drawStringWithShadow(ctx, titleStr, x + 14, y + 10, 0xFFFFAA00);

        if (this.activeTab == Tab.PLACEMENTS) {
            if (this.group != null) {
                List<GroupPlacementData> placements = this.group.getPlacements();
                if (placements.isEmpty()) {
                    String emptyStr = "§7" + StringUtils.translate("schematic_synchronizer.gui.manage_group.no_placements");
                    this.drawString(ctx, emptyStr, x + 16, y + 70, 0xFFAAAAAA);
                } else {
                    int itemY = y + 58;
                    int maxItems = 7;
                    int start = Math.min(scrollOffset, Math.max(0, placements.size() - maxItems));

                    for (int i = start; i < Math.min(placements.size(), start + maxItems); i++) {
                        GroupPlacementData p = placements.get(i);
                        BlockPos pos = p.getOrigin();
                        String posStr = String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
                        String nameLine = "§e" + p.getName() + " §7(" + p.getSchematicId() + ")";
                        this.drawString(ctx, nameLine, x + 16, itemY, 0xFFFFFFFF);

                        String detailLine = "§8" + posStr + " | Rot: " + p.getRotation() + " | Mir: " + p.getMirror();
                        this.drawString(ctx, detailLine, x + 16, itemY + 10, 0xFF888888);

                        itemY += 24;
                    }
                }
            }
        } else if (this.activeTab == Tab.MEMBERS) {
            if (this.group != null) {
                int itemY = y + 58;
                List<Map.Entry<UUID, String>> memberList = new ArrayList<>(this.group.getMembers().entrySet());

                for (Map.Entry<UUID, String> entry : memberList) {
                    UUID memberUuid = entry.getKey();
                    String memberName = entry.getValue();
                    boolean isOwner = this.group.isOwner(memberUuid);

                    String text = isOwner
                            ? "§6§l" + memberName + " §e[" + StringUtils.translate("schematic_synchronizer.gui.group.status.owner") + "]"
                            : "§f" + memberName;
                    this.drawString(ctx, text, x + 16, itemY + 4, 0xFFFFFFFF);
                    itemY += 24;
                }
            }
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}
