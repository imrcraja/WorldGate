package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WardrobeScreen extends Screen {
    private static final Identifier WORLDGATE_CAPE_TEXTURE =
            Identifier.fromNamespaceAndPath("worldgate", "textures/cosmetics/worldgate-cape.png");

    public enum Tab { COSMETICS, EMOTES }

    private final Screen parent;
    private final Tab tab;
    private final List<WorldGateButton> itemButtons = new ArrayList<>();

    private String status = Component.translatable("worldgate.wardrobe.syncing").getString();
    private boolean loading = true;
    private boolean ownedOnly = false;
    private int selectedIndex = 0;

    public WardrobeScreen(Screen parent, Tab tab) {
        super(Component.literal(tab == Tab.EMOTES ? "WORLDGATE EMOTES" : "WORLDGATE SHOP"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    protected void init() {
        itemButtons.clear();

        int controlsY = 56;
        int controlsLeft = Math.max(18, width / 2 - 250);

        addRenderableWidget(new WorldGateButton(controlsLeft, controlsY, 100, 22,
                caps(Component.literal("SHOP")), () -> open(Tab.COSMETICS)));
        addRenderableWidget(new WorldGateButton(controlsLeft + 106, controlsY, 100, 22,
                caps(Component.literal("EMOTES")), () -> open(Tab.EMOTES)));

        final WorldGateButton[] ownedButtonRef = new WorldGateButton[1];
        ownedButtonRef[0] = new WorldGateButton(controlsLeft + 212, controlsY, 118, 22,
                Component.literal("OWNED ONLY: OFF"), () -> {
                    ownedOnly = !ownedOnly;
                    ownedButtonRef[0].setMessage(Component.literal("OWNED ONLY: " + (ownedOnly ? "ON" : "OFF")));
                    selectedIndex = 0;
                    refresh();
                });
        addRenderableWidget(ownedButtonRef[0]);

        addRenderableWidget(new WorldGateButton(controlsLeft + 336, controlsY, 118, 22,
                caps(Component.literal("ELITE COINS")), () -> minecraft.setScreen(new EliteCoinScreen(this))));

        for (int i = 0; i < 6; i++) {
            final int index = i;
            WorldGateButton button = new WorldGateButton(0, 0, 100, 20,
                    Component.literal("LOADING"), () -> {
                        selectedIndex = index;
                        action(index);
                    });
            itemButtons.add(button);
            addRenderableWidget(button);
        }

        addRenderableWidget(new WorldGateButton(width / 2 - 155, height - 30, 100, 22,
                Component.literal("REFRESH"), this::refresh));
        addRenderableWidget(new WorldGateButton(width / 2 - 50, height - 30, 150, 22,
                Component.literal("BACK"), this::onClose, 0xFF9CA9B8));

        refresh();
    }

    private List<EliteCoinManager.Item> items() {
        List<EliteCoinManager.Item> result = new ArrayList<>();
        for (EliteCoinManager.Item item : EliteCoinManager.catalog().items()) {
            boolean match = tab == Tab.EMOTES
                    ? "emote".equalsIgnoreCase(item.type())
                    : "cosmetic".equalsIgnoreCase(item.type());
            if (match && (!ownedOnly || EliteCoinManager.inventory().owns(item.id()))) {
                result.add(item);
            }
        }
        return result;
    }

    private void refresh() {
        status = Component.translatable("worldgate.wardrobe.syncing").getString();
        loading = true;
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    loading = false;
                    status = EliteCoinManager.wallet().available()
                            ? Component.translatable("worldgate.wardrobe.synced", EliteCoinManager.wallet().balance()).getString()
                            : Component.translatable("worldgate.wardrobe.unavailable").getString();
                    updateButtons();
                });
            }
        });
        updateButtons();
    }

    private void updateButtons() {
        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();

        if (selectedIndex >= items.size()) {
            selectedIndex = Math.max(0, items.size() - 1);
        }

        for (int i = 0; i < itemButtons.size(); i++) {
            WorldGateButton button = itemButtons.get(i);
            if (i >= items.size()) {
                button.setMessage(Component.literal("UNAVAILABLE"));
                button.active = false;
                continue;
            }

            EliteCoinManager.Item item = items.get(i);
            button.active = true;

            if (!inv.owns(item.id())) {
                button.setMessage(Component.literal(
                        item.priceCoins() == 0 ? "CLAIM FREE" : ("BUY • " + item.priceCoins() + " EC")));
            } else if (inv.equipped(item.type(), item.id())) {
                button.setMessage(Component.literal("EQUIPPED"));
            } else {
                button.setMessage(Component.literal("EQUIP"));
            }
        }
    }

    private void action(int index) {
        List<EliteCoinManager.Item> items = items();
        if (index < 0 || index >= items.size()) return;

        EliteCoinManager.Item item = items.get(index);

        if (!EliteCoinManager.inventory().owns(item.id())) {
            status = Component.translatable("worldgate.coin.purchasing", item.name()).getString();
            WorldGateModClient.EXECUTOR.submit(() -> {
                String response = EliteCoinManager.purchaseItem(item.id());
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        if (response != null && response.contains("\"ok\":true")) {
                            status = Component.translatable("worldgate.coin.purchased", item.name()).getString();
                        } else if (response != null && response.contains("insufficient_balance")) {
                            status = Component.translatable("worldgate.coin.insufficient").getString();
                        } else {
                            status = Component.translatable("worldgate.coin.purchase_failed").getString();
                        }
                        refresh();
                    });
                }
            });
            return;
        }

        status = Component.translatable("worldgate.wardrobe.equipping", item.name()).getString();
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.equipItem(item.id());
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (response != null && response.contains("\"ok\":true")) {
                        status = Component.translatable("worldgate.wardrobe.equipped_item", item.name()).getString();
                        if ("emote".equalsIgnoreCase(item.type())) {
                            String room = WorldGateModClient.CURRENT_ROOM_CODE;
                            if (room != null && !room.isBlank()) {
                                WorldGateModClient.EMOTE_MANAGER.sendEmote(
                                        room, item.id().substring("emote:".length()));
                                status = Component.translatable("worldgate.wardrobe.used", item.name()).getString();
                            }
                        }
                    } else {
                        status = Component.translatable("worldgate.wardrobe.equip_failed").getString();
                    }
                    refresh();
                });
            }
        });
    }

    private void open(Tab next) {
        if (next != tab && minecraft != null) {
            minecraft.setScreen(new WardrobeScreen(parent, next));
        }
    }

    private int previewX() {
        return width - 270;
    }

    private int previewY() {
        return 94;
    }

    private int previewW() {
        return 252;
    }

    private int previewH() {
        return height - 140;
    }

    private int gridLeft() {
        return 18;
    }

    private int gridTop() {
        return 94;
    }

    private int gridWidth() {
        return Math.max(300, previewX() - gridLeft() - 14);
    }

    private int cardW() {
        return Math.max(120, (gridWidth() - 12) / 2);
    }

    private int cardH() {
        return 94;
    }

    private int cardX(int index) {
        return gridLeft() + (index % 2) * (cardW() + 12);
    }

    private int cardY(int index) {
        return gridTop() + (index / 2) * (cardH() + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();

        // Premium dark backdrop.
        g.fill(0, 0, width, height, 0xF20B1016);
        g.fill(0, 0, width, 82, 0xFF101821);
        g.fill(0, 80, width, 82, 0xFF263441);

        // Header.
        g.text(font, Component.literal("WORLDGATE"), 20, 18, 0xFF72E0FF, false);
        g.text(font, Component.literal(tab == Tab.EMOTES ? "EMOTES" : "SHOP"), 20, 34, 0xFFFFFFFF, false);

        String balance = EliteCoinManager.wallet().available()
                ? "ELITE COINS  " + EliteCoinManager.wallet().balance()
                : "ELITE COINS  —";
        g.text(font, Component.literal(balance), width - font.width(balance) - 20, 23, 0xFFFFD45A, false);

        g.text(font,
                Component.literal(ownedOnly ? "Owned collection" : "Browse and preview your collection"),
                width - font.width(ownedOnly ? "Owned collection" : "Browse and preview your collection") - 20,
                39, 0xFF8F9BA7, false);

        // Item grid.
        for (int i = 0; i < 6; i++) {
            int x = cardX(i);
            int y = cardY(i);
            boolean available = i < items.size();
            boolean selected = available && i == selectedIndex;

            g.fill(x, y, x + cardW(), y + cardH(), available ? 0xFF121B24 : 0xFF0F151C);
            g.outline(x, y, cardW(), cardH(), selected ? 0xFF72E0FF : 0xFF2B3946);

            WorldGateButton button = itemButtons.get(i);
            button.setX(x + 12);
            button.setY(y + cardH() - 28);
            button.setWidth(cardW() - 24);
            button.setHeight(20);

            if (!available) {
                g.text(font, Component.literal("NO ITEM"), x + 14, y + 16, 0xFF56616C, false);
                continue;
            }

            EliteCoinManager.Item item = items.get(i);
            boolean owned = inv.owns(item.id());
            boolean equipped = inv.equipped(item.type(), item.id());

            // Category badge / visual placeholder.
            int badgeColor = "emote".equalsIgnoreCase(item.type()) ? 0xFF8D72FF : 0xFF72E0FF;
            g.fill(x + 12, y + 12, x + 48, y + 48, 0x332C4352);
            g.outline(x + 12, y + 12, 36, 36, badgeColor);

            String glyph = "emote".equalsIgnoreCase(item.type()) ? "E" : "C";
            g.centeredText(font, Component.literal(glyph), x + 30, y + 24, badgeColor);

            g.text(font, Component.literal(item.name()), x + 58, y + 13, 0xFFFFFFFF, false);

            String desc = item.description() == null ? "" : item.description();
            if (desc.length() > 31) desc = desc.substring(0, 28) + "...";
            g.text(font, Component.literal(desc), x + 58, y + 30, 0xFF84919D, false);

            String state = equipped ? "EQUIPPED"
                    : owned ? "OWNED"
                    : item.priceCoins() == 0 ? "FREE"
                    : item.priceCoins() + " EC";
            int stateColor = equipped ? 0xFF72E0FF
                    : owned ? 0xFF70E090
                    : item.priceCoins() == 0 ? 0xFF72E0FF
                    : 0xFFFFD45A;
            g.text(font, Component.literal(state), x + 58, y + 48, stateColor, false);
        }

        // Preview panel.
        int px = previewX();
        int py = previewY();
        int pw = previewW();
        int ph = previewH();

        g.fill(px, py, px + pw, py + ph, 0xFF111A23);
        g.outline(px, py, pw, ph, 0xFF334553);
        g.fill(px, py, px + pw, py + 42, 0xFF182531);
        g.text(font, Component.literal("ITEM PREVIEW"), px + 14, py + 15, 0xFF72E0FF, false);

        if (selectedIndex < items.size()) {
            EliteCoinManager.Item selected = items.get(selectedIndex);
            boolean owned = inv.owns(selected.id());
            boolean equipped = inv.equipped(selected.type(), selected.id());

            g.text(font, Component.literal(selected.name()), px + 14, py + 58, 0xFFFFFFFF, false);

            String typeLabel = "emote".equalsIgnoreCase(selected.type()) ? "EMOTE" : "COSMETIC";
            g.text(font, Component.literal(typeLabel), px + 14, py + 74, 0xFF82909C, false);

            int previewBoxX = px + 18;
            int previewBoxY = py + 94;
            int previewBoxW = pw - 36;
            int previewBoxH = Math.min(170, Math.max(110, ph - 190));

            g.fill(previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + previewBoxH, 0xFF0B1117);
            g.outline(previewBoxX, previewBoxY, previewBoxW, previewBoxH, 0xFF263746);

            if ("cosmetic:worldgate-cape".equals(selected.id())) {
                int capeW = Math.min(180, previewBoxW - 24);
                int capeH = Math.min(90, previewBoxH - 28);
                int capeX = previewBoxX + (previewBoxW - capeW) / 2;
                int capeY = previewBoxY + (previewBoxH - capeH) / 2;
                g.blit(RenderPipelines.GUI_TEXTURED, WORLDGATE_CAPE_TEXTURE,
                        capeX, capeY, 0, 0, capeW, capeH, 64, 32);
            } else {
                String marker = "emote".equalsIgnoreCase(selected.type()) ? "EMOTE" : "COSMETIC";
                g.centeredText(font, Component.literal(marker),
                        previewBoxX + previewBoxW / 2,
                        previewBoxY + previewBoxH / 2 - 4,
                        0xFFB7C6D3);
            }

            String state = equipped ? "EQUIPPED"
                    : owned ? "OWNED • READY TO EQUIP"
                    : selected.priceCoins() == 0 ? "FREE • CLAIMABLE"
                    : selected.priceCoins() + " ELITE COINS";
            int stateColor = selected.priceCoins() == 0 ? 0xFF72E0FF
                    : equipped ? 0xFF72E0FF
                    : owned ? 0xFF70E090
                    : 0xFFFFD45A;

            g.centeredText(font, Component.literal(state), px + pw / 2, py + ph - 52, stateColor);

            if (!owned && selected.priceCoins() == 0) {
                g.centeredText(font, Component.literal("FREE CAPE"),
                        px + pw / 2, py + ph - 34, 0xFF72E0FF);
            } else {
                g.centeredText(font, Component.literal(
                                equipped ? "Currently equipped" : owned ? "Select EQUIP to use" : "Select BUY to unlock"),
                        px + pw / 2, py + ph - 34, 0xFF7F8D99);
            }
        } else {
            g.centeredText(font, Component.literal("Select an item"),
                    px + pw / 2, py + ph / 2, 0xFF6D7B87);
        }

        // Footer/status.
        g.fill(0, height - 48, width, height, 0xFF0D141B);
        g.text(font, Component.literal(status), 18, height - 31, 0xFF8B99A5, false);
        if (loading) {
            WorldGateLoadingAnimation.draw(g, font, 18, height - 45);
        }

        super.extractRenderState(g, mx, my, delta);
    }

    private static Component caps(Component value) {
        return Component.literal(value.getString().toUpperCase(Locale.ROOT));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
