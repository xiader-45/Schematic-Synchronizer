package com.schematicsynchronizer.client.mixin;

import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.client.gui.ButtonServerSchematics;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicPlacements;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.gui.widgets.WidgetContainer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

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
            boolean shared = ClientSchematicManager.getInstance().isPlacementShared(placement);
            ButtonOnOff shareButton = new ButtonOnOff(
                    this.buttonsStartX,
                    y + 1,
                    -1,
                    true,
                    "schematic_synchronizer.gui.button.share_placement",
                    shared
            );
            shareButton.setHoverStrings(StringUtils.translate("schematic_synchronizer.gui.button.hover.share_placement"));
            this.addButton(shareButton, (button, mouseButton) -> {
                boolean newShared = !ClientSchematicManager.getInstance().isPlacementShared(placement);
                ClientSchematicManager.getInstance().setPlacementShared(placement, newShared);
                ((ButtonOnOff) button).updateDisplayString(newShared);
            });
            this.buttonsStartX = shareButton.getX() - 1;
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
}
