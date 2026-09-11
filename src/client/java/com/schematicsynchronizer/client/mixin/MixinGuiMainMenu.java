package com.schematicsynchronizer.client.mixin;

import com.schematicsynchronizer.client.gui.GuiServerSchematicsList;
import fi.dy.masa.litematica.gui.ButtonIcons;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiBase {

    @Inject(method = "initGui", at = @At("RETURN"), remap = false)
    private void addServerSchematicsButton(CallbackInfo ci) {
        int buttonWidth = 160;
        try {
            Method m = GuiMainMenu.class.getDeclaredMethod("getButtonWidth");
            m.setAccessible(true);
            buttonWidth = (int) m.invoke(this);
        } catch (Throwable ignored) {
        }

        // Place in column 2 directly below the "CONFIGURATION" button
        int x = 12 + buttonWidth + 20;
        int y = 52;

        String label = StringUtils.translate("schematic_synchronizer.gui.button.server_schematics");
        ButtonGeneric button = new ButtonGeneric(x, y, buttonWidth, 20, label, ButtonIcons.SCHEMATIC_BROWSER);
        addButton(button, (btn, mouseButton) -> {
            GuiBase.openGui(new GuiServerSchematicsList(this));
        });
    }
}
