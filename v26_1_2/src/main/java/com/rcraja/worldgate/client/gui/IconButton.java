package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A small square icon-only button, styled to sit quietly on top of the
 * vanilla title/pause screens (translucent background, thin outline) rather
 * than the opaque dark panel WorldGateButton uses for the main menu.
 */
public final class IconButton extends AbstractWidget {

    public enum Icon { MAILBOX, BELL }

    private final Icon icon;
    private final Runnable action;

    public IconButton(int x, int y, int size, Icon icon, Runnable action) {
        super(x, y, size, size, Component.empty());
        this.icon = icon;
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        boolean hovered = isHoveredOrFocused();

        int bg = hovered ? 0x40FFFFFF : 0x30000000;
        graphics.fill(getX(), getY(), getRight(), getBottom(), bg);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), hovered ? 0xFFFFFFFF : 0x99FFFFFF);

        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        int color = 0xFFFFFFFF;

        if (icon == Icon.MAILBOX) {
            drawMailIcon(graphics, cx, cy, color);
        } else {
            drawBellIcon(graphics, cx, cy, color);
        }
    }

    private void drawMailIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int w = 12, h = 8;
        int x = cx - w / 2;
        int y = cy - h / 2;
        g.outline(x, y, w, h, color);
        int half = w / 2;
        for (int i = 0; i <= half; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + w - 1 - i, y + i, x + w - i, y + i + 1, color);
        }
    }

    private void drawBellIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int w = 10, h = 9;
        int x = cx - w / 2;
        int y = cy - h / 2;
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 3, color);
        g.fill(x, y + h - 3, x + w, y + h - 2, color);
        g.fill(cx - 1, y + h - 1, cx + 1, y + h, color);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) action.run();
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
