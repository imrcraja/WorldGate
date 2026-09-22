package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.elite.EliteBadgeRenderer;
import com.rcraja.worldgate.client.elite.EliteManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Own WorldGate Elite profile. Spending is shown only to the owner.
 * Entitlement values come from the server/backend read model.
 */
public final class EliteProfileScreen extends Screen {
    private final Screen parent;
    private EliteManager.EliteProfile profile = EliteManager.EliteProfile.unavailable();
    private boolean loading = true;
    private String status = "Loading Elite profile...";

    public EliteProfileScreen(Screen parent) {
        super(Component.literal("WorldGate Elite"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(
                Button.builder(
                        Component.literal("Refresh"),
                        btn -> load()
                ).bounds(this.width / 2 - 102, this.height - 55, 97, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("Back"),
                        btn -> this.minecraft.setScreen(parent)
                ).bounds(this.width / 2 + 3, this.height - 55, 97, 20).build()
        );

        load();
    }

    private void load() {
        loading = true;
        status = "Loading Elite profile...";

        WorldGateModClient.EXECUTOR.submit(() -> {
            EliteManager.refreshOwnProfile();
            EliteManager.EliteProfile loaded = EliteManager.loadOwnProfile();

            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    profile = loaded;
                    loading = false;
                    status = loaded.available()
                            ? "Server-synced Elite profile"
                            : "Elite data is not available yet.";
                });
            }
        });
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;

        graphics.centeredText(
                this.font,
                Component.literal("WorldGate Elite"),
                cx,
                18,
                0xFFFFFFFF
        );

        graphics.centeredText(
                this.font,
                Component.literal(
                        profile.available()
                                ? profile.displayLevel()
                                : "Elite profile"
                ),
                cx,
                31,
                0xFFD8C7FF
        );

        if (profile.hasElite()) {
            EliteBadgeRenderer.draw(
                    graphics,
                    this.font,
                    cx,
                    50,
                    116,
                    profile.level()
            );

            int infoY = 178;
            graphics.centeredText(
                    this.font,
                    Component.literal("Eligible spending: " + money(profile.eligibleSpentMinorUnits())),
                    cx,
                    infoY,
                    0xFFFFFFFF
            );

            if (profile.nextLevelThresholdMinorUnits() > 0) {
                graphics.centeredText(
                        this.font,
                        Component.literal("Remaining to next level: " + money(profile.remainingToNext())),
                        cx,
                        infoY + 16,
                        0xFFBDBDBD
                );
            }

            if (profile.maxLevelThresholdMinorUnits() > 0) {
                graphics.centeredText(
                        this.font,
                        Component.literal("Remaining to max level: " + money(profile.remainingToMax())),
                        cx,
                        infoY + 32,
                        0xFFBDBDBD
                );
            }

            graphics.centeredText(
                    this.font,
                    Component.literal("Badge identity is fixed; the background evolves by level."),
                    cx,
                    infoY + 55,
                    0xFF8F8F8F
            );
        } else {
            graphics.centeredText(
                    this.font,
                    Component.literal(
                            loading
                                    ? "Loading..."
                                    : "Your Elite entitlement has not been published yet."
                    ),
                    cx,
                    92,
                    0xFFAAAAAA
            );
        }

        graphics.centeredText(
                this.font,
                Component.literal(status),
                cx,
                this.height - 76,
                0xFF888888
        );
    }

    private static String money(long minorUnits) {
        return String.format(java.util.Locale.ROOT, "USD %.2f", minorUnits / 100.0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
