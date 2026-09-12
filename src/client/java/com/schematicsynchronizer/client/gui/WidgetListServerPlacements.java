    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        if (this.listContents.isEmpty()) {
            int cy = this.posY + this.totalHeight / 2 - 10;
            String text = StringUtils.translate("schematic_synchronizer.gui.placement_info.empty_list");
            int w = this.getStringWidth(text);
            int cx = this.posX + (this.totalWidth - w) / 2;
            this.drawStringWithShadow(ctx, text, cx, cy, 0xFFAAAAAA);
        }
    }
}
