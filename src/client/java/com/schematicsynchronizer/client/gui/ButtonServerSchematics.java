package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.SchematicSynchronizer;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class ButtonServerSchematics extends ButtonGeneric {
    public static final Identifier ICON_NORMAL = SchematicSynchronizer.id("textures/gui/server_icon.png");
    public static final Identifier ICON_HOVER = SchematicSynchronizer.id("textures/gui/server_icon_highlight.png");

    public ButtonServerSchematics(int x, int y, int width, int height, String label) {
        super(x, y, width, height, label);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean isSelected) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;

        // Draw standard Minecraft / Malilib button background
        if (this.renderDefaultBackground) {
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, this.getTexture(this.hovered), this.x, this.y, this.width, this.height);
        }

        // Draw custom 11x11 server icon (normal or highlight)
        int iconX = this.x + 4;
        int iconY = this.y + (this.height - 11) / 2;
        Identifier icon = this.hovered ? ICON_HOVER : ICON_NORMAL;
        ctx.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0f, 0.0f, 11, 11, 11, 11);

        // Draw button label offset after icon
        if (this.displayString != null && !this.displayString.isEmpty()) {
            int textY = this.y + (this.height - 8) / 2;
            int textColor = !this.enabled ? 0xFFA0A0A0 : (this.hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            int textX = this.x + 4 + 11 + 4;
            this.drawString(ctx, textX, textY, textColor, this.displayString);
        }
    }
}
