package com.schematicsynchronizer.client.gui;

import com.schematicsynchronizer.client.ClientSchematicManager;
import com.schematicsynchronizer.data.PlayerPlacementInfo;
import com.schematicsynchronizer.data.ServerSchematicInfo;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

public class GuiSelectPlacementDialog extends GuiBase {
    private final ServerSchematicInfo schematic;
    private final List<PlayerPlacementInfo> placements;

    public GuiSelectPlacementDialog(Screen parent, ServerSchematicInfo schematic, List<PlayerPlacementInfo> placements) {
        this.schematic = schematic;
        this.placements = placements;
        this.setParent(parent);
        this.title = "Выбор размещения для " + schematic.getName();
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogWidth = 440;
        int rowHeight = 24;
        int dialogHeight = Math.min(this.height - 40, 50 + placements.size() * rowHeight + 35);
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        int y = dialogY + 35;
        for (PlayerPlacementInfo p : placements) {
            final PlayerPlacementInfo placement = p;
            int btnWidth = 140;
            int btnX = dialogX + dialogWidth - btnWidth - 10;

            ButtonGeneric btnPlace = new ButtonGeneric(btnX, y + 2, btnWidth, 20, "Разместить как у него");
            addButton(btnPlace, (btn, mouseBtn) -> {
                ClientSchematicManager.getInstance().placeLikePlayer(placement);
                closeGui(true);
            });

            y += rowHeight;
        }

        ButtonGeneric btnClose = new ButtonGeneric(dialogX + (dialogWidth - 80) / 2, dialogY + dialogHeight - 26, 80, 20, "Закрыть");
        addButton(btnClose, (btn, mouseBtn) -> closeGui(true));
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            closeGui(true);
            return true;
        }
        return super.onKeyTyped(event);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogWidth = 440;
        int rowHeight = 24;
        int dialogHeight = Math.min(this.height - 40, 50 + placements.size() * rowHeight + 35);
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        // Background box
        RenderUtils.drawOutlinedBox(ctx, dialogX, dialogY, dialogWidth, dialogHeight, 0xE0101010, GuiBase.COLOR_HORIZONTAL_BAR);

        // Header
        drawString(ctx, "Выберите игрока для копирования размещения:", dialogX + 12, dialogY + 12, 0xFFFFAA00);

        int y = dialogY + 38;
        for (int i = 0; i < placements.size(); i++) {
            PlayerPlacementInfo p = placements.get(i);
            String dim = p.getDimension();
            if (dim.contains(":")) {
                dim = dim.substring(dim.indexOf(':') + 1);
            }
            String info = "§e" + p.getOwnerName() + " §7| §f" + p.getPos().toShortString() + " §7| §b" + dim + " §7| §f" + p.getRotation();
            drawString(ctx, info, dialogX + 12, y + 6, 0xFFFFFFFF);
            y += rowHeight;
        }
    }
}
