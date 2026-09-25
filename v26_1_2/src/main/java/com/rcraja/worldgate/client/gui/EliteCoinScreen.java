package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class EliteCoinScreen extends Screen {
    private enum Filter { ALL, COSMETICS, EMOTES }

    private final Screen parent;
    private final List<Button> itemButtons = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String status = Component.translatable("worldgate.coin.syncing").getString();

    public EliteCoinScreen(Screen parent) {
        super(Component.translatable("worldgate.coin.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.filter_all"),
                b -> setFilter(Filter.ALL)).bounds(width / 2 - 156, 58, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.filter_cosmetics"),
                b -> setFilter(Filter.COSMETICS)).bounds(width / 2 - 76, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.filter_emotes"),
                b -> setFilter(Filter.EMOTES)).bounds(width / 2 + 28, 58, 76, 20).build());

        int left = Math.max(20, width / 2 - 310);
        int top = 92;
        int gap = 10;
        int cardW = 148;
        int cardH = 92;

        for (int i = 0; i < 8; i++) {
            final int index = i;
            int col = i % 4;
            int row = i / 4;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            Button button = Button.builder(Component.literal("Loading..."),
                    b -> purchase(index))
                    .bounds(x + 10, y + 66, cardW - 20, 20).build();
            itemButtons.add(button);
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.refresh"),
                b -> refresh()).bounds(width / 2 - 155, height - 30, 97, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.claim.title"),
                b -> minecraft.setScreen(new ClaimCenterScreen(this))).bounds(width / 2 - 52, height - 30, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),
                b -> onClose()).bounds(width / 2 + 56, height - 30, 99, 20).build());

        refresh();
    }

    private List<EliteCoinManager.Item> items() {
        List<EliteCoinManager.Item> result = new ArrayList<>();
        for (EliteCoinManager.Item item : EliteCoinManager.catalog().items()) {
            if (filter == Filter.COSMETICS && !"cosmetic".equalsIgnoreCase(item.type())) continue;
            if (filter == Filter.EMOTES && !"emote".equalsIgnoreCase(item.type())) continue;
            result.add(item);
        }
        return result;
    }

    private void setFilter(Filter next) {
        filter = next;
        updateButtons();
    }

    private void refresh() {
        status = "Syncing Elite Store...";
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) minecraft.execute(() -> {
                status = EliteCoinManager.wallet().available()
                        ? Component.translatable("worldgate.coin.synced").getString()
                        : Component.translatable("worldgate.coin.unavailable").getString();
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
                button.active = false;
                button.setMessage(Component.translatable("worldgate.coin.unavailable"));
                continue;
            }
            EliteCoinManager.Item item = items.get(i);
            button.active = !inv.owns(item.id());
            button.setMessage(Component.literal(inv.owns(item.id())
                    ? "Owned"
                    : item.priceCoins() == 0 ? "Unlock" : "Buy " + item.priceCoins() + " EC"));
        }
    }

    private void purchase(int index) {
        List<EliteCoinManager.Item> items = items();
        if (index < 0 || index >= items.size()) return;
        EliteCoinManager.Item item = items.get(index);
        if (EliteCoinManager.inventory().owns(item.id())) {
            status = Component.translatable("worldgate.coin.already_owned").getString();
            return;
        }

        status = Component.translatable("worldgate.coin.purchasing",item.name()).getString();
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.purchaseItem(item.id());
            if (minecraft != null) minecraft.execute(() -> {
                if (response != null && !response.isBlank()) {
                    try {
                        JsonObject result = JsonParser.parseString(response).getAsJsonObject();
                        if (result.has("ok") && result.get("ok").getAsBoolean()) {
                            status = Component.translatable("worldgate.coin.purchased",item.name()).getString();
                        } else {
                            String error = result.has("error")
                                    ? result.get("error").getAsString()
                                    : "";
                            status = "insufficient_balance".equals(error)
                                    ? Component.translatable("worldgate.coin.insufficient").getString()
                                    : error.isBlank()
                                            ? Component.translatable("worldgate.coin.purchase_failed").getString()
                                            : "Purchase failed: " + error;
                        }
                    } catch (Exception ignored) {
                        status = "Purchase could not be completed.";
                    }
                } else {
                    status = Component.translatable("worldgate.coin.purchase_failed").getString();
                }
                refresh();
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int cx = width / 2;
        g.centeredText(font, Component.translatable("worldgate.coin.title"), cx, 18, 0xFFFFFFFF);
        g.centeredText(font,
                Component.translatable("worldgate.coin.balance",
                        Long.toString(EliteCoinManager.wallet().balance())),
                cx, 38, 0xFFFFD45A);
        g.centeredText(font,
                Component.translatable("worldgate.coin.catalog_note").getString(),
                cx, 76, 0xFF9AA7B4);

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
            g.fill(x, y, x + cardW, y + cardH, 0xCC10161D);
            if (i >= items.size()) {
                g.outline(x, y, cardW, cardH, 0xFF2B3742);
                g.centeredText(font, Component.translatable("worldgate.coin.no_item").getString(), x + cardW / 2, y + 34, 0xFF59636D);
                continue;
            }

            EliteCoinManager.Item item = items.get(i);
            boolean owned = inv.owns(item.id());
            g.outline(x, y, cardW, cardH, owned ? 0xFF4B90A8 : 0xFF2B3742);
            g.centeredText(font, Component.literal(item.name()),
                    x + cardW / 2, y + 18, 0xFFFFFFFF);
            g.centeredText(font, Component.literal(item.description()),
                    x + cardW / 2, y + 35, 0xFF8E9AA6);
            g.centeredText(font, Component.literal(
                            owned ? Component.translatable("worldgate.coin.owned_upper").getString() : item.priceCoins() + " EC"),
                    x + cardW / 2, y + 51,
                    owned ? 0xFF70E090 : 0xFFFFD45A);
        }

        g.centeredText(font, Component.literal(status),
                cx, height - 48, 0xFF8E9AA6);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
