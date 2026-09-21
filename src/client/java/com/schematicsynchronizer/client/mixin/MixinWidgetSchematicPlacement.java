package com.schematicsynchronizer.client.mixin;

import com.schematicsynchronizer.client.ClientHologramGroupManager;
import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.client.gui.ButtonServerSchematics;
import com.schematicsynchronizer.client.gui.GuiManageHologramGroup;
import com.schematicsynchronizer.client.gui.GuiSelectGroupForPlacement;
import com.schematicsynchronizer.data.HologramGroupData;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicPlacements;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetContainer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.UUID;

@Mixin(value = WidgetSchematicPlacement.class, remap = false)
public abstract class MixinWidgetSchematicPlacement extends WidgetContainer {
    @Shadow
    public SchematicPlacement placement;

    @Shadow
    public int buttonsStartX;

    public MixinWidgetSchematicPlacement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(int x, int y, int width, int height, boolean isOdd,
                        SchematicPlacement placement, int listIndex,
                        WidgetListSchematicPlacements parent, CallbackInfo ci) {
		if (!ClientSchematicManager.getInstance().isPlacedFromServer(placement)) {
			return;
		}

        // Group sync button: either "In Group <name>" or "Add to group"
        HologramGroupData group = ClientHologramGroupManager.getInstance().getGroupForPlacement(placement);
        if (group != null) {
            String label = StringUtils.translate("schematic_synchronizer.gui.button.in_group", group.getName());
            int w = StringUtils.getStringWidth(label) + 10;
            ButtonGeneric groupButton = new ButtonGeneric(this.buttonsStartX - w, y + 1, w, 20, label);
            groupButton.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.button.hover.in_group", group.getName()));
            this.addButton(groupButton, (button, mouseButton) -> {
                GuiBase.openGui(new GuiManageHologramGroup(null, group));
            });
            this.buttonsStartX = groupButton.getX() - 1;
        } else {
            String label = StringUtils.translate("schematic_synchronizer.gui.button.add_to_group");
            int w = StringUtils.getStringWidth(label) + 10;
            ButtonGeneric groupButton = new ButtonGeneric(this.buttonsStartX - w, y + 1, w, 20, label);
            groupButton.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.button.hover.add_to_group"));
            this.addButton(groupButton, (button, mouseButton) -> {
                GuiBase.openGui(new GuiSelectGroupForPlacement(null, placement));
            });
            this.buttonsStartX = groupButton.getX() - 1;
        }

        updateGroupPlacementButtons();
    }

    @Unique
    private void updateGroupPlacementButtons() {
		if (!ClientSchematicManager.getInstance().isPlacedFromServer(placement)) {
			return;
		}

        ButtonBase btnRemove = null;
        ButtonBase btnOnOff = null;
        ButtonBase btnConfig = null;

        if (this.subWidgets.size() >= 3) {
            if (this.subWidgets.get(0) instanceof ButtonBase b0) btnRemove = b0;
            if (this.subWidgets.get(1) instanceof ButtonBase b1) btnOnOff = b1;
            if (this.subWidgets.get(2) instanceof ButtonBase b2) btnConfig = b2;
        }

        HologramGroupData group = ClientHologramGroupManager.getInstance().getGroupForPlacement(this.placement);
        if (group != null) {
            Minecraft mc = Minecraft.getInstance();
            UUID myUuid = (mc.player != null) ? mc.player.getUUID() : (mc.getUser() != null ? mc.getUser().getProfileId() : null);
            String myName = (mc.player != null) ? mc.player.getName().getString() : (mc.getUser() != null ? mc.getUser().getName() : null);

            boolean isOwner = group.isOwner(myUuid, myName);
            boolean isOp = ClientHologramGroupManager.getInstance().canOpManage();
            boolean canManage = isOwner || isOp;

            boolean canEdit = canManage;
            if (!canEdit) {
                String perm = group.getMemberPermission(myUuid, myName);
                if (HologramGroupData.PERM_ALL.equals(perm) || HologramGroupData.PERM_EDIT.equals(perm)) {
                    canEdit = true;
                }
            }

            boolean customVis = ClientHologramGroupManager.getInstance().isCustomVisibilityEnabled(group.getId());

            if (btnRemove != null) {
                btnRemove.setEnabled(canManage);
                if (!canManage) {
                    btnRemove.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.manage_group.hover.delete_only_owner"));
                } else {
                    btnRemove.setHoverStrings();
                }
            }

            if (btnConfig != null) {
                btnConfig.setEnabled(canEdit);
                if (!canEdit) {
                    btnConfig.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.placement.hover.no_edit_perm"));
                } else {
                    btnConfig.setHoverStrings(StringUtils.translate("litematica.gui.button.schematic_placements.configure"));
                }
            }

            if (btnOnOff != null) {
                boolean onOffEnabled = canManage || customVis;
                btnOnOff.setEnabled(onOffEnabled);
                if (!onOffEnabled) {
                    btnOnOff.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.placement.hover.sync_locked"));
                } else {
                    btnOnOff.setHoverStrings();
                }
            }
        } else {
            if (btnRemove != null) {
                btnRemove.setEnabled(true);
                btnRemove.setHoverStrings();
            }
            if (btnConfig != null) {
                btnConfig.setEnabled(true);
                btnConfig.setHoverStrings(StringUtils.translate("litematica.gui.button.schematic_placements.configure"));
            }
            if (btnOnOff != null) {
                btnOnOff.setEnabled(true);
                btnOnOff.setHoverStrings();
            }
        }
    }

    @Unique
    private int getServerIconX() {
        int iconX = this.buttonsStartX - 13;
        if (this.placement.isRegionPlacementModified()) {
            iconX -= 13;
        }
        if (this.placement.isLocked() && iconX == this.buttonsStartX - 26) {
            iconX -= 13;
        }
        return iconX;
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void onRenderHead(GuiContext ctx, int mouseX, int mouseY, boolean isOdd, CallbackInfo ci) {
        updateGroupPlacementButtons();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void onRender(GuiContext ctx, int mouseX, int mouseY, boolean isOdd, CallbackInfo ci) {
        if (ClientSchematicManager.getInstance().isPlacedFromServer(this.placement)) {
            int iconX = getServerIconX();
            int iconY = this.y + 6;

            boolean hovered = mouseX >= iconX && mouseX < iconX + 11 && mouseY >= iconY && mouseY < iconY + 11;
            Identifier texture = hovered ? ButtonServerSchematics.ICON_HOVER : ButtonServerSchematics.ICON_NORMAL;
            ctx.blit(RenderPipelines.GUI_TEXTURED, texture, iconX, iconY, 0.0f, 0.0f, 11, 11, 11, 11);
        }
    }

    @Inject(method = "postRenderHovered", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPostRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean isOdd, CallbackInfo ci) {
        if (ClientSchematicManager.getInstance().isPlacedFromServer(this.placement)) {
            int iconX = getServerIconX();
            int iconY = this.y + 6;

            if (mouseX >= iconX && mouseX < iconX + 11 && mouseY >= iconY && mouseY < iconY + 11) {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, Collections.singletonList(
                        StringUtils.translate("schematic_synchronizer.gui.placement.hover.server_schematic")
                ));
                ci.cancel();
            }
        }
    }

    @Inject(method = "postRenderHovered", at = @At("RETURN"), remap = false)
    private void onPostRenderHoveredButtons(GuiContext ctx, int mouseX, int mouseY, boolean isOdd, CallbackInfo ci) {
        for (WidgetBase widget : this.subWidgets) {
            if (widget instanceof ButtonBase btn && btn.hasHoverText()) {
                if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth() &&
                    mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                    RenderUtils.drawHoverText(ctx, mouseX, mouseY, btn.getHoverStrings());
                }
            }
        }
    }
}
