package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WorldGateMenuProfile extends AbstractWidget {
    private final Minecraft minecraft;

    public WorldGateMenuProfile(int x, int y, int width, int height, Minecraft minecraft) {
        super(x, y, width, height, Component.literal("WorldGate profile"));
        this.minecraft = minecraft;
    }

    public static void addPreview(Consumer<AbstractWidget> add, Minecraft minecraft,
                                   int panelX, int panelY, int panelWidth, int previewSize,
                                   Runnable wardrobeAction) {
        int previewX = panelX + (panelWidth - previewSize) / 2;
        int previewY = panelY + 28;
        PlayerSkinWidget preview = new PlayerSkinWidget(
                previewSize, previewSize + 30, minecraft.getEntityModels(),
                () -> minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false).get());
        preview.setPosition(previewX, previewY);
        add.accept(preview);

        add.accept(new WorldGateButton(
                previewX + previewSize / 2 - 10, previewY + previewSize + 6, 20, 20,
                Component.empty(), wardrobeAction,
                0xFFB8C4D0, WorldGateButton.Icon.WARDROBE));
    }

    public static void refreshOwned() {
        try {
            EliteCoinManager.refresh();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        g.fill(getX(), getY(), getRight(), getBottom(), 0x5A101821);
        g.outline(getX(), getY(), getWidth(), getHeight(), 0x663D5263);
        g.text(Minecraft.getInstance().font, Component.literal("WORLDGATE"),
                getX() + 14, getY() + 10, 0xFF72DFFF);
        g.text(Minecraft.getInstance().font, Component.literal("PROFILE"),
                getRight() - 57, getY() + 10, 0xFF8E9AA6);

        String name = minecraft.getUser().getName();
        g.centeredText(Minecraft.getInstance().font, Component.literal(name),
                getX() + getWidth() / 2, getY() + 184, 0xFFFFFFFF);

        int ownedY = getY() + 210;
        g.text(Minecraft.getInstance().font, Component.literal("OWNED / CLAIMED"),
                getX() + 14, ownedY, 0xFFBFDFFF);

        List<EliteCoinManager.Item> owned = new ArrayList<>();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();
        try {
            for (EliteCoinManager.Item item : EliteCoinManager.catalog().items()) {
                if (item != null && inv.owns(item.id())) owned.add(item);
            }
        } catch (Throwable ignored) {
        }

        if (owned.isEmpty()) {
            g.text(Minecraft.getInstance().font, Component.literal("No claimed or purchased items yet."),
                    getX() + 14, ownedY + 20, 0xFF7E8B98);
            return;
        }

        int max = Math.min(5, owned.size());
        for (int i = 0; i < max; i++) {
            EliteCoinManager.Item item = owned.get(i);
            int rowY = ownedY + 20 + i * 25;
            g.fill(getX() + 12, rowY - 3, getRight() - 12, rowY + 17, 0x321E2A35);
            String itemName = item.name() == null || item.name().isBlank() ? item.id() : item.name();
            if (itemName.length() > 22) itemName = itemName.substring(0, 21) + "…";
            g.text(Minecraft.getInstance().font, Component.literal(itemName),
                    getX() + 20, rowY + 2, 0xFFEAF2F8);
            String state = "OWNED";
            try {
                if (inv.equipped(item.type(), item.id())) state = "EQUIPPED";
            } catch (Throwable ignored) {
            }
            g.text(Minecraft.getInstance().font, Component.literal(state),
                    getRight() - ("EQUIPPED".equals(state) ? 62 : 48), rowY + 2,
                    "EQUIPPED".equals(state) ? 0xFF72E0FF : 0xFF70E090);
        }

        if (owned.size() > max) {
            g.text(Minecraft.getInstance().font, Component.literal("+" + (owned.size() - max) + " more"),
                    getX() + 14, ownedY + 20 + max * 25, 0xFF7E8B98);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        // Display-only panel.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
