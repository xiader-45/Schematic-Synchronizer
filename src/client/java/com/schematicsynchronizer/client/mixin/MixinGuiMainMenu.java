package com.schematicsynchronizer.client.mixin;

import com.schematicsynchronizer.client.gui.ButtonHologramGroups;
import com.schematicsynchronizer.client.gui.ButtonServerSchematics;
import com.schematicsynchronizer.client.gui.GuiHologramGroups;
import com.schematicsynchronizer.client.gui.GuiServerSchematicsList;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiBase {

    @Inject(method = "initGui", at = @At("RETURN"), remap = false)
    private void addServerButtons(CallbackInfo ci) {
        int buttonWidth = 160;
        try {
            Method m = GuiMainMenu.class.getDeclaredMethod("getButtonWidth");
            m.setAccessible(true);
            buttonWidth = (int) m.invoke(this);
        } catch (Throwable ignored) {
        }

        // Column 2, directly below the "CONFIGURATION" button
        int x = 12 + buttonWidth + 20;
        int ySchem = 52;

        String schemLabel = StringUtils.translate("schematic_synchronizer.gui.button.server_schematics");
        ButtonServerSchematics btnSchem = new ButtonServerSchematics(x, ySchem, buttonWidth, 20, schemLabel);
        addButton(btnSchem, (btn, mouseButton) -> {
            GuiBase.openGui(new GuiServerSchematicsList(this));
        });

        // Directly below our server schematics button: y = 52 + 22 = 74
        int yGroups = 74;
        String groupLabel = StringUtils.translate("schematic_synchronizer.gui.button.hologram_groups");
        ButtonHologramGroups btnGroups = new ButtonHologramGroups(x, yGroups, buttonWidth, 20, groupLabel);
        addButton(btnGroups, (btn, mouseButton) -> {
            GuiBase.openGui(new GuiHologramGroups(this));
        });
    }
}
