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
    private final Icon icon;

    public enum Icon { NONE, HOST, SOCIAL, WARDROBE, FEATURES, PICTURES, SETTINGS, ACCOUNT }

    public WorldGateButton(int x, int y, int width, int height, Component message, Runnable action) {
        this(x, y, width, height, message, event -> action.run(), 0xFF67D8FF, Icon.NONE);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message, Runnable action, int accent) {
        this(x, y, width, height, message, event -> action.run(), accent, Icon.NONE);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message,
                           Runnable action, int accent, Icon icon) {
        this(x, y, width, height, message, event -> action.run(), accent, icon);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message,
                           Consumer<MouseButtonEvent> action, int accent) {
        this(x, y, width, height, message, action, accent, Icon.NONE);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message,
                           Consumer<MouseButtonEvent> action, int accent, Icon icon) {
        super(x, y, width, height, message);
        this.action = action;
        this.accent = accent;
        this.icon = icon == null ? Icon.NONE : icon;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        boolean hovered = isHoveredOrFocused();
        boolean pressed = isFocused() && hovered;

        int shadow = hovered ? 0x2C000000 : 0x18000000;
        int glass = hovered ? 0x684C667A : 0x402C3A4A;
        int glassInner = hovered ? 0x3DFFFFFF : 0x24FFFFFF;
        int edge = hovered ? accent : 0x6B9FB3C5;
        int shine = hovered ? 0x66FFFFFF : 0x3DFFFFFF;
        int textColor = !active ? 0xFF687583 : hovered ? 0xFFFFFFFF : 0xFFE8EEF4;

        graphics.fill(getX() + 2, getY() + 3, getRight() + 2, getBottom() + 4, shadow);
        graphics.fill(getX(), getY(), getRight(), getBottom(), glass);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, glassInner);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), edge);
        graphics.fill(getX() + 3, getY() + 2, getRight() - 3, getY() + 3, shine);

        if (pressed) {
            graphics.fill(getX() + 2, getBottom() - 3, getRight() - 2, getBottom() - 2, accent);
        }

        int textX = getX() + getWidth() / 2;
        if (icon != Icon.NONE) {
            int iconX = getRight() - 13;
            drawIcon(graphics, iconX, getY() + getHeight() / 2, icon, textColor);
            textX = getX() + (getWidth() - 20) / 2;
        }

        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                textX, getY() + (getHeight() - 9) / 2, textColor);
    }

    private static void drawIcon(GuiGraphicsExtractor g, int cx, int cy, Icon icon, int color) {
        switch (icon) {
            case HOST -> {
                g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
                g.fill(cx - 3, cy + 2, cx + 3, cy + 4, color);
                g.fill(cx - 1, cy - 5, cx + 1, cy - 3, color);
            }
            case SOCIAL -> {
                g.fill(cx - 5, cy - 4, cx - 1, cy, color);
                g.fill(cx + 1, cy - 1, cx + 5, cy + 3, color);
                g.fill(cx - 6, cy + 2, cx - 2, cy + 5, color);
            }
            case WARDROBE -> {
                g.fill(cx - 4, cy - 5, cx + 4, cy - 3, color);
                g.fill(cx - 5, cy - 2, cx + 5, cy + 5, color);
            }
            case FEATURES -> {
                g.fill(cx - 6, cy - 5, cx + 6, cy - 3, color);
                g.fill(cx - 4, cy - 1, cx + 4, cy + 1, color);
                g.fill(cx - 2, cy + 3, cx + 2, cy + 5, color);
            }
            case PICTURES -> {
                g.outline(cx - 6, cy - 5, 12, 10, color);
                g.fill(cx - 4, cy + 1, cx - 1, cy + 3, color);
                g.fill(cx - 1, cy - 1, cx + 4, cy + 3, color);
            }
            case SETTINGS -> {
                g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
                g.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
                g.fill(cx - 3, cy - 3, cx + 3, cy + 3, color);
            }
            case ACCOUNT -> {
                g.fill(cx - 3, cy - 5, cx + 3, cy + 1, color);
                g.fill(cx - 5, cy + 2, cx + 5, cy + 5, color);
            }
            default -> { }
        }
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