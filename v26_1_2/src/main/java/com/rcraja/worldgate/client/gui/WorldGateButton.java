package com.rcraja.worldgate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class WorldGateButton extends AbstractWidget {
    private final Consumer<MouseButtonEvent> action;
    private final int accent;

    public WorldGateButton(int x, int y, int width, int height, Component message, Runnable action) {
        this(x, y, width, height, message, event -> action.run(), 0xFF67D8FF);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message,
                           Consumer<MouseButtonEvent> action, int accent) {
        super(x, y, width, height, message);
        this.action = action;
        this.accent = accent;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        boolean hovered = isHoveredOrFocused();
        int outer = hovered ? 0xE51B2633 : 0xD9121A24;
        int inner = hovered ? 0xF024303E : 0xED171F29;
        graphics.fill(getX() + 2, getY() + 3, getRight() + 2, getBottom() + 3, 0x65000000);
        graphics.fill(getX(), getY(), getRight(), getBottom(), outer);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), hovered ? accent : 0xFF2D3B49);
        graphics.fill(getX(), getY(), getX() + 3, getBottom(), hovered ? accent : 0xFF24313D);
        graphics.fill(getX() + 4, getY() + 2, getRight() - 2, getBottom() - 2, inner);
        int textColor = !active ? 0xFF687583 : hovered ? 0xFFFFFFFF : 0xFFE5EBF1;
        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 9) / 2, textColor);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) action.accept(event);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
