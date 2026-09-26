package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WardrobeScreen extends Screen {
    public enum Tab { COSMETICS, EMOTES }

    private final Screen parent;
    private final Tab tab;
    private final List<Button> itemButtons = new ArrayList<>();
    private String status = Component.translatable("worldgate.wardrobe.syncing").getString();
    private boolean loading = true;
    private boolean ownedOnly = false;

    public WardrobeScreen(Screen parent, Tab tab) {
        super(Component.translatable(tab == Tab.EMOTES ? "worldgate.wardrobe.emotes" : "worldgate.wardrobe.cosmetics"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("worldgate.wardrobe.cosmetics"),
                b -> open(Tab.COSMETICS))
                .bounds(width / 2 - 156, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.wardrobe.emotes"),
                b -> open(Tab.EMOTES))
                .bounds(width / 2 - 52, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Owned: OFF"),
                b -> {
                    ownedOnly = !ownedOnly;
                    b.setMessage(Component.literal("Owned: " + (ownedOnly ? "ON" : "OFF")));
                    refresh();
                })
                .bounds(width / 2 + 156, 58, 92, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.shop"),
                b -> minecraft.setScreen(new EliteCoinScreen(this)))
                .bounds(width / 2 + 52, 58, 100, 20).build());

        int left = Math.max(20, width / 2 - 310);
        int top = 108;
        int gap = 10;
        int cardW = 148;
        int cardH = 92;

        for (int i = 0; i < 8; i++) {
            final int index = i;
            int col = i % 4;
            int row = i / 4;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            Button button = Button.builder(Component.translatable("worldgate.loading"),
                    b -> action(index))
                    .bounds(x + 10, y + 66, cardW - 20, 20).build();
            itemButtons.add(button);
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.refresh"),
                b -> refresh())
                .bounds(width / 2 - 155, height - 30, 97, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),
                b -> onClose())
                .bounds(width / 2 - 52, height - 30, 207, 20).build());

        refresh();
    }

    private List<EliteCoinManager.Item> items() {
        List<EliteCoinManager.Item> result = new ArrayList<>();
        for (EliteCoinManager.Item item : EliteCoinManager.catalog().items()) {
            boolean match = tab == Tab.EMOTES
                    ? "emote".equalsIgnoreCase(item.type())
                    : "cosmetic".equalsIgnoreCase(item.type());
            if (match && (!ownedOnly || EliteCoinManager.inventory().owns(item.id()))) result.add(item);
        }
        return result;
    }

    private void refresh() {
        status = Component.translatable("worldgate.wardrobe.syncing").getString();
        loading = true;
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) minecraft.execute(() -> {
                loading = false;
                status = EliteCoinManager.wallet().available()
                        ? Component.translatable("worldgate.wardrobe.synced",EliteCoinManager.wallet().balance()).getString()
                        : Component.translatable("worldgate.wardrobe.unavailable").getString();
                updateButtons();
            });
        });
        updateButtons();
    }

    private void updateButtons() {
        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();
        for (int i = 0; i < itemButtons.size(); i++) {
            Button button = itemButtons.get(i);
            if (i >= items.size()) {
                button.setMessage(Component.translatable("worldgate.coin.unavailable"));
                button.active = false;
                continue;
            }
            EliteCoinManager.Item item = items.get(i);
            button.active = true;
            if (!inv.owns(item.id())) {
                button.setMessage(Component.literal(item.priceCoins() == 0
                        ? Component.translatable("worldgate.coin.unlock").getString()
                        : Component.translatable("worldgate.coin.buy_item_price", item.priceCoins()).getString()));
            } else if (inv.equipped(item.type(), item.id())) {
                button.setMessage(Component.translatable("worldgate.wardrobe.equipped"));
            } else {
                button.setMessage(Component.translatable(item.type().equals("emote") ? "worldgate.wardrobe.equip_use" : "worldgate.wardrobe.equip"));
            }
        }
    }

    private void action(int index) {
        List<EliteCoinManager.Item> items = items();
        if (index < 0 || index >= items.size()) return;
        EliteCoinManager.Item item = items.get(index);
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();

        if (!inv.owns(item.id())) {
            status = Component.translatable("worldgate.coin.purchasing",item.name()).getString();
            WorldGateModClient.EXECUTOR.submit(() -> {
                String response = EliteCoinManager.purchaseItem(item.id());
                if (minecraft != null) minecraft.execute(() -> {
                    if (response != null && response.contains("\"ok\":true")) {
                        status = Component.translatable("worldgate.coin.purchased",item.name()).getString();
                    } else if (response != null && response.contains("insufficient_balance")) {
                        status = Component.translatable("worldgate.coin.insufficient").getString();
                    } else {
                        status = Component.translatable("worldgate.coin.purchase_failed").getString();
                    }
                    refresh();
                });
            });
            return;
        }

        status = Component.translatable("worldgate.wardrobe.equipping",item.name()).getString();
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.equipItem(item.id());
            if (minecraft != null) minecraft.execute(() -> {
                if (response != null && response.contains("\"ok\":true")) {
                    status = Component.translatable("worldgate.wardrobe.equipped_item",item.name()).getString();
                    if ("emote".equalsIgnoreCase(item.type())) {
                        String room = WorldGateModClient.CURRENT_ROOM_CODE;
                        if (room != null && !room.isBlank()) {
                            WorldGateModClient.EMOTE_MANAGER.sendEmote(room, item.id().substring("emote:".length()));
                            status = Component.translatable("worldgate.wardrobe.used",item.name()).getString();
                        }
                    }
                } else {
                    status = Component.translatable("worldgate.wardrobe.equip_failed").getString();
                }
                refresh();
            });
        });
    }

    private void open(Tab next) {
        if (next != tab && minecraft != null) {
            minecraft.setScreen(new WardrobeScreen(parent, next));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        g.centeredText(font, Component.literal(ownedOnly ? "Owned items only" : "All available items"),
                width / 2, 50, 0xFF7DE2FF);

        g.centeredText(font, tab == Tab.EMOTES ? Component.translatable("worldgate.wardrobe.emotes") : Component.translatable("worldgate.wardrobe.cosmetics"),
                width / 2, 20, 0xFFFFFFFF);
        g.centeredText(font,
                tab == Tab.EMOTES
                        ? Component.translatable("worldgate.wardrobe.emote_note").getString()
                        : Component.translatable("worldgate.wardrobe.cosmetic_note").getString(),
                width / 2, 38, 0xFF9AA7B4);

        int left = Math.max(20, width / 2 - 310);
        int top = 92;
        int gap = 10;
        int cardW = 148;
        int cardH = 92;
        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();

        for (int i = 0; i < 8; i++) {
            int col = i % 4;
            int row = i / 4;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            boolean available = i < items.size();
            g.fill(x, y, x + cardW, y + cardH, available ? 0x4A10161D : 0x2810161D);
            g.outline(x, y, cardW, cardH,
                    available && inv.equipped(items.get(i).type(), items.get(i).id())
                            ? 0xFF7DE2FF : 0xFF65727F);

            if (!available) {
                g.centeredText(font, Component.translatable("worldgate.coin.no_item").getString(), x + cardW / 2, y + 34, 0xFF59636D);
                continue;
            }

            EliteCoinManager.Item item = items.get(i);
            boolean owned = inv.owns(item.id());
            boolean equipped = inv.equipped(item.type(), item.id());
            g.centeredText(font, Component.literal(item.name()),
                    x + cardW / 2, y + 18, 0xFFFFFFFF);
            g.centeredText(font, Component.literal(item.description()),
                    x + cardW / 2, y + 35, 0xFF8E9AA6);
            String state = equipped ? Component.translatable("worldgate.wardrobe.equipped_upper").getString() : owned ? Component.translatable("worldgate.coin.owned_upper").getString() : item.priceCoins() + " EC";
            g.centeredText(font, Component.literal(state),
                    x + cardW / 2, y + 51,
                    equipped ? 0xFF7DE2FF : owned ? 0xFF70E090 : 0xFFFFD45A);
        }

        g.centeredText(font, Component.literal(status),
                width / 2, height - 48, 0xFF8E9AA6);
        if (loading) WorldGateLoadingAnimation.draw(g, font, width / 2, height - 62);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
