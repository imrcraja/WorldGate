package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.UserProfileCache;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.elite.EliteBadgeRenderer;
import com.rcraja.worldgate.client.elite.EliteManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

public final class EliteProfileScreen extends Screen {
    private final Screen parent;
    private EliteManager.EliteProfile profile = EliteManager.EliteProfile.unavailable();
    private boolean loading = true;
    private String status = "Loading Elite profile...";

    public EliteProfileScreen(Screen parent) {
        super(Component.translatable("worldgate.elite.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bottom = height - 30;
        addRenderableWidget(new WorldGateButton(width / 2 - 164, bottom, 104, 22,
                Component.translatable("worldgate.coin.shop"),
                () -> minecraft.setScreen(new EliteCoinScreen(this)), 0xFFFFD45A));
        addRenderableWidget(new WorldGateButton(width / 2 - 52, bottom, 104, 22,
                Component.translatable("worldgate.button.refresh"), this::load, 0xFF73E0A1));
        addRenderableWidget(new WorldGateButton(width / 2 + 60, bottom, 104, 22,
                Component.translatable("worldgate.button.back"), () -> minecraft.setScreen(parent), 0xFF9CA9B8));
        addRenderableWidget(new WorldGateButton(width / 2 + 128, 129, 72, 20,
                Component.literal("Copy ID"),
                () -> {
                    String id = UserProfileCache.value("publicId", "");
                    if (id != null && !id.isBlank() && !"Not set".equals(id)) CopyValue.copy(id, "Public ID");
                }, 0xFF73C8E8));
        addRenderableWidget(new WorldGateButton(width / 2 + 128, 153, 72, 20,
                Component.literal("Copy UID"),
                () -> {
                    if (WorldGateModClient.SESSION.isReady()) CopyValue.copy(WorldGateModClient.SESSION.uid(), "UID");
                }, 0xFF73C8E8));

        if (minecraft != null) {
            try {
                int size = 96;
                PlayerSkinWidget preview = new PlayerSkinWidget(
                        size, size + 26, minecraft.getEntityModels(),
                        () -> minecraft.getSkinManager().getInsecureSkin(minecraft.getGameProfile()));
                preview.setPosition(Math.max(18, width / 2 - 250), 72);
                addRenderableWidget(preview);
            } catch (Exception ignored) {
                status = "Profile ready without 3D preview.";
            }
        }
        load();
    }

    private void load() {
        loading = true;
        status = "Syncing profile...";
        WorldGateModClient.EXECUTOR.submit(() -> {
            EliteManager.refreshOwnProfile();
            if (WorldGateModClient.SESSION.isReady()) {
                String uid = WorldGateModClient.SESSION.uid();
                String raw = WorldGateModClient.FRIEND_MANAGER.getProfile(uid);
                if (raw != null && !raw.isBlank() && !"null".equals(raw)) UserProfileCache.save(raw);
                WorldGateModClient.FRIEND_MANAGER.myFriendCode();
            }
            EliteManager.EliteProfile loaded = EliteManager.loadOwnProfile();
            if (minecraft != null) minecraft.execute(() -> {
                profile = loaded;
                loading = false;
                status = loaded.available()
                        ? "Server-synced Elite profile"
                        : "Using local profile cache; Elite entitlement is unavailable right now.";
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {int cx = width / 2;
        int cardX = Math.max(18, cx - 258);
        int cardY = 52;
        int cardW = Math.min(516, width - 36);
        int cardH = Math.min(330, height - 112);

        graphics.blurBeforeThisStratum();
        graphics.fill(0, 0, width, height, 0xA9080D14);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x66334252);
        graphics.outline(cardX, cardY, cardW, cardH, 0x706F8293);

        graphics.text(font, Component.translatable("worldgate.elite.title"), cardX + 18, cardY + 16, 0xFFF5F7FA);

        String displayName = UserProfileCache.value("displayName", "Player");
        String publicId = UserProfileCache.value("publicId", "Not set");
        graphics.text(font, Component.literal(displayName), cardX + 132, cardY + 42, 0xFFFFFFFF);
        graphics.text(font, Component.literal("PUBLIC ID"), cardX + 132, cardY + 62, 0xFF9AA7B4);
        graphics.text(font, clipped("Public ID: " + (publicId == null ? "—" : publicId), 250), cardX + 132, cardY + 77, 0xFF7DE2FF);
        String uid = WorldGateModClient.SESSION.isReady() ? WorldGateModClient.SESSION.uid() : "";
        if (uid != null && !uid.isBlank()) {
            graphics.text(font, clipped("UID: " + uid, 250), cardX + 132, cardY + 92, 0xFFB8C2CC);
        }

        if (profile.hasElite()) {
            EliteBadgeRenderer.draw(graphics, font, cx + 104, cardY + 96, 92, profile.level());
            graphics.text(font, Component.literal("ELITE LEVEL " + profile.level()), cardX + 132, cardY + 108, 0xFFD8C7FF);
            graphics.text(font, Component.literal("Eligible spending: " + money(profile.eligibleSpentMinorUnits())),
                    cardX + 132, cardY + 130, 0xFFFFFFFF);
            if (profile.nextLevelThresholdMinorUnits() > 0) {
                graphics.text(font, Component.literal("Remaining to next: " + money(profile.remainingToNext())),
                        cardX + 132, cardY + 148, 0xFFBFC9D3);
            }
            if (profile.maxLevelThresholdMinorUnits() > 0) {
                graphics.text(font, Component.literal("Remaining to max: " + money(profile.remainingToMax())),
                        cardX + 132, cardY + 166, 0xFFBFC9D3);
            }
            graphics.text(font, Component.translatable("worldgate.elite.badge_note"),
                    cardX + 132, cardY + 190, 0xFF8F9BA8);
        } else {
            graphics.text(font, Component.translatable(loading ? "worldgate.loading" : "worldgate.elite.not_published"),
                    cardX + 132, cardY + 112, 0xFFBFC9D3);
        }

        graphics.text(font, Component.literal("PROFILE STATUS"), cardX + 18, cardY + cardH - 50, 0xFF7DE2FF);
        graphics.text(font, clipped(status, cardW - 36), cardX + 18, cardY + cardH - 34, 0xFF9AA7B4);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private String clipped(String value, int maxWidth) {
        if (value == null) return "";
        String out = value;
        while (font.width(out) > maxWidth && out.length() > 5) {
            out = out.substring(0, out.length() - 1);
        }
        return out.equals(value) ? out : out.substring(0, Math.max(1, out.length() - 3)) + "...";
    }

    private static String money(long n) {
        return String.format(java.util.Locale.ROOT, "USD %.2f", n / 100.0);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
