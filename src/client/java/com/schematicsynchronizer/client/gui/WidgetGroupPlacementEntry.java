package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.client.util.PlayerSkinHelper;
import com.schematicsynchronizer.data.GroupPlacementData;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Collections;
import java.util.UUID;

public class WidgetGroupPlacementEntry extends WidgetListEntryBase<GroupPlacementData> {
    private final boolean isOdd;
    private final WidgetListGroupPlacements parentList;
    private ButtonGeneric btnRemove;
    private ButtonOnOff btnOnOff;
    private ButtonGeneric btnConfig;
    private int buttonsStartX;

    public WidgetGroupPlacementEntry(int x, int y, int width, int height,
                                    GroupPlacementData entry, int listIndex,
                                    WidgetListGroupPlacements parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = (listIndex % 2 == 1);
        this.parentList = parentList;

        HologramGroupData group = parentList.getParentGui().getGroup();
        int btnX = x + width - 2;
        int btnY = y + (height - 20) / 2;

        // 1. Remove placement button
        if (group != null && entry != null) {
            this.btnRemove = new ButtonGeneric(btnX, btnY, -1, true, "schematic_synchronizer.gui.manage_group.remove_placement");
            this.addButton(this.btnRemove, (btn, mouse) -> {
                ClientHologramGroupManager.getInstance().removePlacementFromGroup(group.getId(), entry.getId());
                parentList.getParentGui().refreshList();
            });
            btnX = this.btnRemove.getX() - 1;
        }

        // 2. On/off button & 3. Configure placement button
        SchematicPlacement sp = (group != null && entry != null)
                ? ClientHologramGroupManager.getInstance().getPlacementForGroupPlacement(group.getId(), entry.getId())
                : null;
        if (sp != null) {
            this.btnOnOff = new ButtonOnOff(btnX, btnY, -1, true, "litematica.gui.button.schematic_placements.placement_enabled", sp.isEnabled());
            this.addButton(this.btnOnOff, (btn, mouse) -> {
                sp.toggleEnabled();
                this.btnOnOff.updateDisplayString(sp.isEnabled());
                ClientHologramGroupManager.getInstance().onPlacementModified(sp);
            });
            btnX = this.btnOnOff.getX() - 2;

            this.btnConfig = new ButtonGeneric(btnX, btnY, -1, true, "litematica.gui.button.schematic_placements.configure");
            this.addButton(this.btnConfig, (btn, mouse) -> {
                GuiPlacementConfiguration gui = new GuiPlacementConfiguration(sp);
                gui.setParent(parentList.getParentGui());
                GuiBase.openGui(gui);
            });
            btnX = this.btnConfig.getX() - 1;
        }

        this.buttonsStartX = btnX;
    }

    private int getServerIconX() {
        return this.buttonsStartX - 14;
    }

    private boolean isServerSchematic() {
        return this.entry != null && ClientSchematicManager.getInstance().getSchematic(this.entry.getSchematicId()) != null;
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent event) {
        return false;
    }

    private String formatRotation(String rot) {
        if (rot == null) return "0°";
        return switch (rot) {
            case "CLOCKWISE_90" -> "90°";
            case "CLOCKWISE_180" -> "180°";
            case "COUNTERCLOCKWISE_90" -> "270°";
            default -> "0°";
        };
    }

    private String formatMirror(String mir) {
        if (mir == null) return StringUtils.translate("schematic_synchronizer.gui.mirror.none");
        return switch (mir) {
            case "LEFT_RIGHT" -> StringUtils.translate("schematic_synchronizer.gui.mirror.left_right");
            case "FRONT_BACK" -> StringUtils.translate("schematic_synchronizer.gui.mirror.front_back");
            default -> StringUtils.translate("schematic_synchronizer.gui.mirror.none");
        };
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        HologramGroupData group = this.parentList.getParentGui().getGroup();
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
        String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);

        boolean isOwner = (group != null && group.isOwner(myUuid, myName));
        boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
        boolean canManage = isOwner || isOp;

        boolean canEdit = canManage;
        if (!canEdit && group != null) {
            String perm = group.getMemberPermission(myUuid, myName);
            if (HologramGroupData.PERM_ALL.equals(perm) || HologramGroupData.PERM_EDIT.equals(perm)) {
                canEdit = true;
            }
        }

        boolean customVis = (group != null) && ClientHologramGroupManager.getInstance().isCustomVisibilityEnabled(group.getId());

        if (this.btnRemove != null) {
            this.btnRemove.setEnabled(canManage);
            if (!canManage) {
                this.btnRemove.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_only_owner"));
            } else {
                this.btnRemove.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.remove_placement"));
            }
        }
        if (this.btnConfig != null) {
            this.btnConfig.setEnabled(canEdit);
            if (!canEdit) {
                this.btnConfig.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.placement.hover.no_edit_perm"));
            } else {
                this.btnConfig.setHoverStrings(StringUtils.translate("litematica.gui.button.schematic_placements.configure"));
            }
        }
        if (this.btnOnOff != null) {
            boolean onOffEnabled = canManage || customVis;
            this.btnOnOff.setEnabled(onOffEnabled);
            if (!onOffEnabled) {
                this.btnOnOff.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.placement.hover.sync_locked"));
            } else {
                this.btnOnOff.setHoverStrings();
            }
            SchematicPlacement sp = (group != null && this.entry != null)
                    ? ClientHologramGroupManager.getInstance().getPlacementForGroupPlacement(group.getId(), this.entry.getId())
                    : null;
            if (sp != null) {
                this.btnOnOff.updateDisplayString(sp.isEnabled());
            }
        }

        if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x38FFFFFF);
        }

        if (this.entry == null) return;

        // Player head of creator with 1px border HEX #3573ff
        UUID creatorUuid = this.entry.getCreatorUuid();
        if (creatorUuid == null && group != null) {
            creatorUuid = group.getOwnerUuid();
        }
        String creatorName = (group != null && creatorUuid != null) ? group.getMembers().get(creatorUuid) : null;
        if (creatorName == null && group != null) {
            creatorName = group.getOwnerName();
        }

        int headSize = 10;
        int headX = this.x + 5;
        int headY = this.y + (this.height - headSize) / 2;

        try {
            PlayerSkin skin = PlayerSkinHelper.getSkinForPlayer(creatorUuid, creatorName);
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(ctx, skin, headX, headY, headSize);
            }
        } catch (Throwable ignored) {
        }
        RenderUtils.drawOutline(ctx, headX - 1, headY - 1, headSize + 2, headSize + 2, 0xFF3573FF);

        int textX = headX + headSize + 6;
        int textY = this.y + 3;

        // Line 1: Name and schematic file
        String nameLine = "§e" + this.entry.getName() + " §7(" + this.entry.getSchematicId() + ")";
        if (this.entry.isLocked()) {
            nameLine += " §c[§4" + StringUtils.translate("schematic_synchronizer.gui.manage_group.locked") + "§c]";
        }
        this.drawString(ctx, textX, textY, 0xFFFFFFFF, nameLine);

        // Line 2: Origin, rotation, mirror
        BlockPos pos = this.entry.getOrigin();
        String posStr = String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
        String rotStr = "Rot: " + formatRotation(this.entry.getRotation());
        String mirStr = "Mir: " + formatMirror(this.entry.getMirror());
        String detailLine = "§f" + posStr + "  §7|  §f" + rotStr + "  §7|  §f" + mirStr;
        this.drawString(ctx, textX, textY + 11, 0xFFFFFFFF, detailLine);

        super.render(ctx, mouseX, mouseY, selected);
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean isOdd) {
        super.postRenderHovered(ctx, mouseX, mouseY, isOdd);

        drawButtonHover(ctx, mouseX, mouseY, this.btnRemove);
        drawButtonHover(ctx, mouseX, mouseY, this.btnConfig);
        drawButtonHover(ctx, mouseX, mouseY, this.btnOnOff);

        if (isServerSchematic()) {
            int iconX = getServerIconX();
            int iconY = this.y + (this.height - 11) / 2;
            if (mouseX >= iconX && mouseX < iconX + 11 && mouseY >= iconY && mouseY < iconY + 11) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(
                        StringUtils.translate("schematic_synchronizer.gui.placement.hover.server_schematic")
                ));
            }
        }
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
