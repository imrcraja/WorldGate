package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class NotificationsScreen extends Screen {
    private final Screen parent;
    private String status = "Syncing notifications...";

    public NotificationsScreen(Screen parent) {
        super(Component.literal("Notifications"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new WorldGateButton(width - 116, height - 34, 100, 24,
                Component.literal("Claim Center"), () -> minecraft.setScreen(new ClaimCenterScreen(this)),
                0xFFBDA6FF));
        addRenderableWidget(new WorldGateButton(width - 224, height - 34, 100, 24,
                Component.literal("Refresh"), this::refresh, 0xFF73E0A1));
        addRenderableWidget(new WorldGateButton(16, height - 34, 100, 24,
                Component.literal("Back"), this::onClose, 0xFF9CA9B8));
        refresh();
    }

    private void refresh() {
        status = "Syncing notifications...";
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) {
                minecraft.execute(() -> status = EliteCoinManager.hasNotification()
                        ? "You have new WorldGate activity."
                        : "You're all caught up.");
            }
        });
    }

    private void markRead(String id) {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.markRead(id);
            if (minecraft != null) minecraft.execute(this::refresh);
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.blurBeforeThisStratum();
        g.fill(0, 0, width, height, 0xD9080C12);

        int margin = Math.max(16, Math.min(36, width / 24));
        int panelW = Math.min(640, width - margin * 2);
        int left = (width - panelW) / 2;
        int top = 36;

        g.fill(left, top, left + panelW, height - 48, 0xE50C121A);
        g.outline(left, top, panelW, height - 84, 0xFF293541);
        g.text(font, Component.literal("NOTIFICATIONS"), left + 18, top + 14, 0xFFF5F7FA);
        g.text(font, Component.literal(status), left + 18, top + 31, 0xFF7C8997);

        int y = top + 56;
        int rowW = panelW - 36;
        int rowH = 54;

        EliteCoinManager.RewardStatus rewards = EliteCoinManager.rewardStatus();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean daily = !today.toString().equals(rewards.lastClaimDate())
                && rewards.cycleCoins() < rewards.maxCycleCoins();
        boolean activity = rewards.activityEnabled()
                && rewards.activityUsedToday() < rewards.activityDailyCap();

        if (daily) {
            drawNotice(g, left + 18, y, rowW, rowH,
                    "Daily Elite Coin reward is available.",
                    "Open Claim Center to claim today's reward.", 0xFF67D8FF);
            y += rowH + 8;
        }
        if (activity) {
            drawNotice(g, left + 18, y, rowW, rowH,
                    "Activity reward is available.",
                    "Open Claim Center to start the activity timer.", 0xFF73E0A1);
            y += rowH + 8;
        }

        List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
        if (mail.isEmpty() && !daily && !activity) {
            g.text(font, "No new notifications.", left + 18, y + 10, 0xFF68737E);
        } else {
            for (int i = 0; i < Math.min(6, mail.size()); i++) {
                EliteCoinManager.Mail m = mail.get(i);
                int ry = y + i * (rowH + 8);
                String sender = m.fromName() == null || m.fromName().isBlank() ? "WorldGate" : m.fromName();
                String title = sender + "  +" + m.coins() + " EC";
                String body = m.message() == null || m.message().isBlank()
                        ? "Elite Coin mailbox message."
                        : m.message();
                if (body.length() > 62) body = body.substring(0, 62) + "...";
                drawNotice(g, left + 18, ry, rowW, rowH, title, body,
                        "UNREAD".equalsIgnoreCase(m.status()) ? 0xFFFFD36B : 0xFF52606D);
                if ("UNREAD".equalsIgnoreCase(m.status())) {
                    addRenderableWidget(new WorldGateButton(left + panelW - 108, ry + 14, 72, 24,
                            Component.literal("Read"), () -> markRead(m.id()), 0xFFFFD36B));
                }
            }
        }
    }

    private void drawNotice(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            String title, String body, int accent) {
        g.fill(x, y, x + w, y + h, 0xED171F29);
        g.outline(x, y, w, h, 0xFF2D3B49);
        g.fill(x, y, x + 3, y + h, accent);
        g.text(font, title, x + 12, y + 10, 0xFFE5EBF1);
        g.text(font, body, x + 12, y + 27, 0xFF8E9AA6);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
