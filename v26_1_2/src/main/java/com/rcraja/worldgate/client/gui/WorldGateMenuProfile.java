package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WorldGateMenuProfile {
    private WorldGateMenuProfile() {}

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

    public static void render(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                              Minecraft minecraft, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0x5A101821);
        g.outline(x, y, width, height, 0x663D5263);
        g.text(font, Component.literal("WORLDGATE"), x + 14, y + 10, 0xFF72DFFF);
        g.text(font, Component.literal("PROFILE"), x + width - 57, y + 10, 0xFF8E9AA6);

        String name = minecraft.getUser().getName();
        g.centeredText(font, Component.literal(name), x + width / 2, y + 184, 0xFFFFFFFF);

        int ownedY = y + 210;
        g.text(font, Component.literal("OWNED / CLAIMED"), x + 14, ownedY, 0xFFBFDFFF);

        List<EliteCoinManager.Item> owned = new ArrayList<>();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();
        try {
            for (EliteCoinManager.Item item : EliteCoinManager.catalog().items()) {
                if (item != null && inv.owns(item.id())) owned.add(item);
            }
        } catch (Throwable ignored) {
        }

        if (owned.isEmpty()) {
            g.text(font, Component.literal("No claimed or purchased items yet."),
                    x + 14, ownedY + 20, 0xFF7E8B98);
            return;
        }

        int max = Math.min(5, owned.size());
        for (int i = 0; i < max; i++) {
            EliteCoinManager.Item item = owned.get(i);
            int rowY = ownedY + 20 + i * 25;
            g.fill(x + 12, rowY - 3, x + width - 12, rowY + 17, 0x321E2A35);
            String itemName = item.name() == null || item.name().isBlank() ? item.id() : item.name();
            if (itemName.length() > 22) itemName = itemName.substring(0, 21) + "…";
            g.text(font, Component.literal(itemName), x + 20, rowY + 2, 0xFFEAF2F8);
            String state = "EQUIPPED";
            try {
                if (!inv.equipped(item.type(), item.id())) state = "OWNED";
            } catch (Throwable ignored) {
            }
            g.text(font, Component.literal(state), x + width - 62, rowY + 2,
                    "EQUIPPED".equals(state) ? 0xFF72E0FF : 0xFF70E090);
        }

        if (owned.size() > max) {
            g.text(font, Component.literal("+" + (owned.size() - max) + " more"),
                    x + 14, ownedY + 20 + max * 25, 0xFF7E8B98);
        }
    }
}
