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
    private final java.util.ArrayList<WorldGateButton> readButtons = new java.util.ArrayList<>();

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

        // Read buttons are created once during init. Never mutate the screen's
        // widget list from extractRenderState/render; doing so can cause
        // ConcurrentModificationException and duplicate buttons every frame.
        for (int i = 0; i < 6; i++) {
            final int index = i;
            WorldGateButton read = new WorldGateButton(0, 0, 72, 24,
                    Component.literal("Read"), () -> markReadAt(index), 0xFFFFD36B);
            read.visible = false;
            readButtons.add(read);
            addRenderableWidget(read);
        }

        refresh();
    }

    private void refresh() {
        status = "Syncing notifications...";
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = EliteCoinManager.hasNotification()
                            ? "You have new WorldGate activity."
                            : "You're all caught up.";
                    layoutReadButtons();
                });
            }
        });
    }

    private void markReadAt(int index) {
        List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
        if (index < 0 || index >= mail.size()) return;
        EliteCoinManager.Mail message = mail.get(index);
        if (message == null || message.id() == null || message.id().isBlank()) return;

        WorldGateModClient.EXECUTOR.submit(() -> {
            EliteCoinManager.markRead(message.id());
            if (minecraft != null) minecraft.execute(this::refresh);
        });
    }

    private void layoutReadButtons() {
        if (minecraft == null) return;

        EliteCoinManager.RewardStatus rewards = EliteCoinManager.rewardStatus();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean daily = !today.toString().equals(rewards.lastClaimDate())
                && rewards.cycleCoins() < rewards.maxCycleCoins();
        boolean activity = rewards.activityEnabled()
                && rewards.activityUsedToday() < rewards.activityDailyCap();

        int margin = Math.max(16, Math.min(36, width / 24));
        int panelW = Math.min(640, width - margin * 2);
        int left = (width - panelW) / 2;
        int y = 36 + 56;
        int rowH = 54;

        if (daily) y += rowH + 8;
        if (activity) y += rowH + 8;

        List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
        for (int i = 0; i < readButtons.size(); i++) {
            WorldGateButton button = readButtons.get(i);
            if (i < mail.size()) {
                EliteCoinManager.Mail m = mail.get(i);
                boolean unread = m != null && "UNREAD".equalsIgnoreCase(m.status());
                button.visible = unread;
                button.setPosition(left + panelW - 108, y + i * (rowH + 8) + 14);
            } else {
                button.visible = false;
            }
        }
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
                // The corresponding Read widget is laid out once from init/refresh;
                // no widget mutation is performed during rendering.
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
