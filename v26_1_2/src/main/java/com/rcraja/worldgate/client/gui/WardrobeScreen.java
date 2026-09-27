package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

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

        addRenderableWidget(new WorldGateButton(width / 2 - 250, 58, 100, 22,
                caps(Component.literal("SHOP")), () -> open(Tab.COSMETICS)));
        addRenderableWidget(new WorldGateButton(width / 2 - 146, 58, 100, 22,
                caps(Component.literal("EMOTES")), () -> open(Tab.EMOTES)));

        final WorldGateButton[] ownedButtonRef = new WorldGateButton[1];
        ownedButtonRef[0] = new WorldGateButton(width / 2 - 38, 58, 118, 22,
                Component.literal("OWNED ONLY: OFF"), () -> {
                    ownedOnly = !ownedOnly;
                    ownedButtonRef[0].setMessage(Component.literal("OWNED ONLY: " + (ownedOnly ? "ON" : "OFF")));
                    selectedIndex = 0;
                    refresh();
                });
        addRenderableWidget(ownedButtonRef[0]);

        addRenderableWidget(new WorldGateButton(width / 2 + 86, 58, 118, 22,
                caps(Component.literal("ELITE COINS")), () -> minecraft.setScreen(new EliteCoinScreen(this))));

        int top = 108;
        int gap = 10;
        int cardW = 150;
        int cardH = 92;
        int left = Math.max(18, width / 2 - 350);

        for (int i = 0; i < 6; i++) {
            final int index = i;
            int col = i % 3;
            int row = i / 3;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            WorldGateButton button = new WorldGateButton(x + 10, y + 66, cardW - 20, 20,
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
                        ? Component.translatable("worldgate.wardrobe.synced", EliteCoinManager.wallet().balance()).getString()
                        : Component.translatable("worldgate.wardrobe.unavailable").getString();
                updateButtons();
            });
        });
        updateButtons();
    }

    private void updateButtons() {
        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();
        if (selectedIndex >= items.size()) selectedIndex = Math.max(0, items.size() - 1);

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
                button.setMessage(Component.literal(item.priceCoins() == 0 ? "CLAIM FREE" : ("BUY • " + item.priceCoins() + " EC")));
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
                if (minecraft != null) minecraft.execute(() -> {
                    if (response != null && response.contains("\"ok\":true")) {
                        status = Component.translatable("worldgate.coin.purchased", item.name()).getString();
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

        status = Component.translatable("worldgate.wardrobe.equipping", item.name()).getString();
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.equipItem(item.id());
            if (minecraft != null) minecraft.execute(() -> {
                if (response != null && response.contains("\"ok\":true")) {
                    status = Component.translatable("worldgate.wardrobe.equipped_item", item.name()).getString();
                    if ("emote".equalsIgnoreCase(item.type())) {
                        String room = WorldGateModClient.CURRENT_ROOM_CODE;
                        if (room != null && !room.isBlank()) {
                            WorldGateModClient.EMOTE_MANAGER.sendEmote(room, item.id().substring("emote:".length()));
                            status = Component.translatable("worldgate.wardrobe.used", item.name()).getString();
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
        g.centeredText(font, Component.literal(ownedOnly ? "OWNED ITEMS" : "WORLDGATE SHOP"),
                width / 2, 20, 0xFFFFFFFF);
        g.centeredText(font,
                Component.literal(tab == Tab.EMOTES
                        ? "Select an emote"
                        : "Select a cosmetic to preview it before claiming or equipping"),
                width / 2, 38, 0xFF9AA7B4);

        int top = 108;
        int gap = 10;
        int cardW = 150;
        int cardH = 92;
        int left = Math.max(18, width / 2 - 350);

        List<EliteCoinManager.Item> items = items();
        EliteCoinManager.Inventory inv = EliteCoinManager.inventory();

        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            boolean available = i < items.size();
            boolean selected = available && i == selectedIndex;

            g.fill(x, y, x + cardW, y + cardH, available ? 0x4A10161D : 0x2810161D);
            g.outline(x, y, cardW, cardH, selected ? 0xFF72E0FF : 0xFF65727F);

            if (!available) {
                g.centeredText(font, Component.literal("NO ITEM"), x + cardW / 2, y + 34, 0xFF59636D);
                continue;
            }

            EliteCoinManager.Item item = items.get(i);
            boolean owned = inv.owns(item.id());
            boolean equipped = inv.equipped(item.type(), item.id());

            g.centeredText(font, Component.literal(item.name()),
                    x + cardW / 2, y + 18, 0xFFFFFFFF);
            g.centeredText(font, Component.literal(item.description()),
                    x + cardW / 2, y + 35, 0xFF8E9AA6);
            String state = equipped ? "EQUIPPED" : owned ? "OWNED" : item.priceCoins() == 0 ? "FREE" : item.priceCoins() + " EC";
            g.centeredText(font, Component.literal(state), x + cardW / 2, y + 51,
                    equipped ? 0xFF72E0FF : owned ? 0xFF70E090 : item.priceCoins() == 0 ? 0xFF72E0FF : 0xFFFFD45A);
        }

        // FreeFire-style item preview pane: no player dummy, only the selected cosmetic/emote.
        int previewX = width - 250;
        int previewY = 108;
        int previewW = 220;
        int previewH = 222;
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0x5A101821);
        g.outline(previewX, previewY, previewW, previewH, 0x663D5263);
        g.text(font, Component.literal("PREVIEW"), previewX + 14, previewY + 12, 0xFF72DFFF);

        if (selectedIndex < items.size()) {
            EliteCoinManager.Item selected = items.get(selectedIndex);
            g.centeredText(font, Component.literal(selected.name()),
                    previewX + previewW / 2, previewY + 36, 0xFFFFFFFF);

            if ("cosmetic:worldgate-cape".equals(selected.id())) {
                g.fill(previewX + 28, previewY + 56, previewX + previewW - 28, previewY + 170, 0x321E2A35);
                g.blit(RenderPipelines.GUI_TEXTURED, WORLDGATE_CAPE_TEXTURE,
                        previewX + 40, previewY + 72, 0, 0, 160, 80, 64, 32);
                g.centeredText(font, Component.literal("FREE CAPE"),
                        previewX + previewW / 2, previewY + 184, 0xFF72E0FF);
            } else {
                g.fill(previewX + 48, previewY + 58, previewX + previewW - 48, previewY + 156, 0x321E2A35);
                g.centeredText(font, Component.literal(tab == Tab.EMOTES ? "EMOTE" : "COSMETIC"),
                        previewX + previewW / 2, previewY + 102, 0xFFBFDFFF);
            }

            boolean owned = inv.owns(selected.id());
            boolean equipped = inv.equipped(selected.type(), selected.id());
            String action = equipped ? "EQUIPPED" : owned ? "EQUIP" : selected.priceCoins() == 0 ? "CLAIM FREE" : "BUY";
            g.centeredText(font, Component.literal(action),
                    previewX + previewW / 2, previewY + 206, equipped ? 0xFF72E0FF : 0xFFFFFFFF);
        } else {
            g.centeredText(font, Component.literal("Select an item"),
                    previewX + previewW / 2, previewY + 105, 0xFF7E8B98);
        }

        g.centeredText(font, Component.literal(status), width / 2 - 35, height - 48, 0xFF8E9AA6);
        if (loading) WorldGateLoadingAnimation.draw(g, font, width / 2 - 35, height - 62);
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
