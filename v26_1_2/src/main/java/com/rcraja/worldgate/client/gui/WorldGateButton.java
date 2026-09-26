package com.rcraja.worldgate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * WorldGate's glass/proxy button.
 *
 * This class is only used by WorldGate widgets. Minecraft's own Button widgets
 * are never wrapped or restyled by this renderer.
 */
public final class WorldGateButton extends AbstractWidget {
    private final Consumer<MouseButtonEvent> action;
    private final int accent;

    public WorldGateButton(int x, int y, int width, int height, Component message, Runnable action) {
        this(x, y, width, height, message, event -> action.run(), 0xFF67D8FF);
    }

    public WorldGateButton(int x, int y, int width, int height, Component message, Runnable action, int accent) {
        this(x, y, width, height, message, event -> action.run(), accent);
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
        boolean pressed = isFocused() && hovered;

        // Layered translucent surfaces give WorldGate its liquid/frosted-glass look
        // without touching Minecraft's own rendering pipeline or OpenGL state.
        int shadow = hovered ? 0x52000000 : 0x46000000;
        int glass = hovered ? 0xD51D2935 : 0xC9121A25;
        int glassInner = hovered ? 0xA92D3946 : 0x8F202A35;
        int edge = hovered ? accent : 0x6F9AA9B8;
        int shine = hovered ? 0x3FFFFFFF : 0x24FFFFFF;
        int textColor = !active ? 0xFF687583 : hovered ? 0xFFFFFFFF : 0xFFE8EEF4;

        // Soft drop shadow.
        graphics.fill(getX() + 2, getY() + 3, getRight() + 2, getBottom() + 4, shadow);

        // Glass body with a slightly inset inner pane.
        graphics.fill(getX(), getY(), getRight(), getBottom(), glass);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, glassInner);

        // Thin accent edge and top glass reflection.
        graphics.outline(getX(), getY(), getWidth(), getHeight(), edge);
        graphics.fill(getX() + 3, getY() + 2, getRight() - 3, getY() + 3, shine);

        // Small vertical reflection keeps the panel looking like translucent glass.
        graphics.fill(getX() + 2, getY() + 5, getX() + 3, getBottom() - 5, 0x30FFFFFF);

        // Pressed state gets a restrained inner highlight rather than changing layout.
        if (pressed) {
            graphics.fill(getX() + 2, getBottom() - 3, getRight() - 2, getBottom() - 2, accent);
        }

        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 9) / 2,
                textColor);
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
