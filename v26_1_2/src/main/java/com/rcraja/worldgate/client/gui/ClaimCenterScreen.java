package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ClaimCenterScreen extends Screen {
    private final Screen parent;
    private final String initialUid;
    private EditBox uidBox;
    private EditBox amountBox;
    private EditBox messageBox;
    private Button dailyButton;
    private Button activityButton;
    private final List<Button> readButtons = new ArrayList<>();
    private Button historyButton;
    private boolean showHistory;
    private String status = "Syncing...";
    private long activityEndsAt;
    private String activitySession;

    public ClaimCenterScreen(Screen parent) {
        this(parent, "");
    }

    public ClaimCenterScreen(Screen parent, String initialUid) {
        super(Component.literal("Claim Center"));
        this.parent = parent;
        this.initialUid = initialUid == null ? "" : initialUid;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        uidBox = new EditBox(font, cx - 145, 66, 290, 20, Component.literal("Recipient Public ID"));
        uidBox.setMaxLength(12);
        uidBox.setValue(initialUid);
        uidBox.setHint(Component.literal("7-12 digit Public ID"));
        addRenderableWidget(uidBox);

        amountBox = new EditBox(font, cx - 145, 105, 70, 20, Component.literal("Coins"));
        amountBox.setMaxLength(9);
        amountBox.setValue("10");
        addRenderableWidget(amountBox);

        messageBox = new EditBox(font, cx - 65, 105, 210, 20, Component.literal("Message"));
        messageBox.setMaxLength(300);
        messageBox.setHint(Component.literal("Optional message"));
        addRenderableWidget(messageBox);

        dailyButton = Button.builder(Component.literal("Claim Daily"),
                b -> claimDaily())
                .bounds(cx - 145, 136, 92, 20).build();
        addRenderableWidget(dailyButton);

        activityButton = Button.builder(Component.literal("Activity Reward"),
                b -> startActivity())
                .bounds(cx - 47, 136, 110, 20).build();
        addRenderableWidget(activityButton);

        addRenderableWidget(Button.builder(Component.literal("Send Gift"),
                b -> gift())
                .bounds(cx + 69, 136, 76, 20).build());

        for (int i = 0; i < 6; i++) {
            final int index = i;
            Button read = Button.builder(Component.literal("Read"),
                    b -> readMailbox(index))
                    .bounds(cx + 100, 190 + i * 28, 45, 18).build();
            read.active = false;
            readButtons.add(read);
            addRenderableWidget(read);
        }

        historyButton = Button.builder(Component.literal("History"),
                b -> {
                    showHistory = !showHistory;
                    historyButton.setMessage(Component.literal(showHistory ? "Mailbox" : "History"));
                    updateState();
                })
                .bounds(cx - 47, height - 30, 92, 20).build();
        addRenderableWidget(historyButton);

        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                b -> refresh())
                .bounds(cx - 145, height - 30, 92, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
                b -> onClose())
                .bounds(cx + 50, height - 30, 95, 20).build());

        refresh();
    }

    private void refresh() {
        status = "Syncing...";
        EliteCoinManager.refresh(() -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = EliteCoinManager.wallet().available()
                            ? "Server synced"
                            : "Coin data unavailable.";
                    updateState();
                });
            }
        });
        updateState();
    }

    private void updateState() {
        EliteCoinManager.RewardStatus rewards = EliteCoinManager.rewardStatus();
        dailyButton.active = rewards.lastClaimDate() == null
                || !java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString().equals(rewards.lastClaimDate());
        activityButton.active = activitySession == null
                && rewards.activityEnabled()
                && rewards.activityUsedToday() < rewards.activityDailyCap();

        List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
        for (int i = 0; i < readButtons.size(); i++) {
            if (i < mail.size()) {
                readButtons.get(i).active = !showHistory && "UNREAD".equalsIgnoreCase(mail.get(i).status());
            } else {
                readButtons.get(i).active = false;
            }
        }
    }

    private void claimDaily() {
        dailyButton.active = false;
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.claimDaily();
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = parseReason(response, "Daily reward claimed.", "Daily reward is already claimed.");
                    refresh();
                });
            }
        });
    }

    private void startActivity() {
        if (activitySession != null) return;
        activityButton.active = false;

        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.startActivity();
            String id = null;
            long expiresAt = 0;
            try {
                JsonObject object = JsonParser.parseString(response == null ? "{}" : response).getAsJsonObject();
                if (object.has("id")) id = object.get("id").getAsString();
                if (object.has("expiresAt")) expiresAt = object.get("expiresAt").getAsLong();
            } catch (Exception ignored) {
            }

            final String session = id;
            final long end = expiresAt > 0 ? expiresAt : System.currentTimeMillis() + 20_000L;
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (session == null || session.isBlank()) {
                        status = "Activity reward is unavailable or at today's limit.";
                        updateState();
                        return;
                    }
                    activitySession = session;
                    activityEndsAt = end;
                    status = "Activity started. Stay here until the timer ends.";
                    updateState();
                });
            }
        });
    }

    private void gift() {
        String uid = uidBox.getValue().trim();
        if (!uid.matches("\\d{7,12}")) { status = "Enter a valid 7-12 digit Public ID."; return; }
        long coins;
        try {
            coins = Long.parseLong(amountBox.getValue().trim());
        } catch (Exception e) {
            status = "Enter a valid Elite Coin amount.";
            return;
        }

        if (uid.isEmpty() || coins <= 0) {
            status = "Enter a recipient Public ID and a positive amount.";
            return;
        }

        String message = messageBox.getValue().trim();
        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.gift(uid, coins, message);
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = parseGiftError(response);
                    refresh();
                });
            }
        });
    }

    private void readMailbox(int index) {
        List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
        if (index < 0 || index >= mail.size()) return;
        EliteCoinManager.Mail message = mail.get(index);
        if (!"UNREAD".equalsIgnoreCase(message.status())) return;

        WorldGateModClient.EXECUTOR.submit(() -> {
            String response = EliteCoinManager.markRead(message.id());
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = response != null && response.contains("\"ok\":true")
                            ? "Message marked as read."
                            : "Could not update that message.";
                    refresh();
                });
            }
        });
    }

    private String parseReason(String response, String success, String already) {
        if (response == null) return "Reward request failed.";
        try {
            JsonObject object = JsonParser.parseString(response).getAsJsonObject();
            String reason = object.has("reason") ? object.get("reason").getAsString() : "";
            if ("claimed".equals(reason)) return success;
            if ("already_claimed".equals(reason)) return already;
            return "Reward is unavailable right now.";
        } catch (Exception ignored) {
            return "Reward request failed.";
        }
    }

    private String parseGiftError(String response) {
        if (response == null || response.isBlank()) return "Gift request failed.";
        try {
            JsonObject object = JsonParser.parseString(response).getAsJsonObject();
            if (object.has("ok") && object.get("ok").getAsBoolean()) {
                return "Gift sent successfully.";
            }
            String error = object.has("error") && !object.get("error").isJsonNull()
                    ? object.get("error").getAsString()
                    : "";
            return switch (error) {
                case "insufficient_balance" -> "Not enough Elite Coins.";
                case "recipient_not_found" -> "Recipient was not found.";
                case "self_gift" -> "You cannot gift yourself.";
                case "invalid_amount" -> "Enter a valid Elite Coin amount.";
                default -> error.isBlank() ? "Gift could not be sent." : "Gift failed: " + error;
            };
        } catch (Exception ignored) {
            return "Gift request failed.";
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (activitySession != null) {
            long remaining = activityEndsAt - System.currentTimeMillis();
            if (remaining <= 0) {
                String id = activitySession;
                activitySession = null;
                status = "Completing activity reward...";
                WorldGateModClient.EXECUTOR.submit(() -> {
                    String response = EliteCoinManager.completeActivity(id);
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            status = response != null && response.contains("\"reason\":\"rewarded\"")
                                    ? "Activity reward claimed."
                                    : "Activity reward could not be claimed.";
                            refresh();
                        });
                    }
                });
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        int cx = width / 2;
        g.centeredText(font, Component.literal("CLAIM CENTER"), cx, 18, 0xFFFFFFFF);
        g.centeredText(font,
                Component.literal("Earn, gift and manage your Elite Coins"),
                cx, 36, 0xFF9AA7B4);
        g.centeredText(font,
                Component.literal(EliteCoinManager.wallet().balance() + " EC"),
                cx, 50, 0xFFFFD45A);

        g.text(font, "GIFT ELITE COINS", cx - 145, 56, 0xFFD8C7FF);
        g.text(font, "Recipient Public ID", cx - 145, 60, 0xFF7E8994);
        g.text(font, "Coins", cx - 145, 99, 0xFF7E8994);
        g.text(font, "Message", cx - 65, 99, 0xFF7E8994);

        int panelY = 174;
        g.fill(cx - 155, panelY, cx + 155, panelY + 190, 0xCC10161D);
        g.outline(cx - 155, panelY, 310, 190, 0xFF2B3742);
        g.text(font, showHistory ? "TRANSACTION HISTORY" : "MAILBOX", cx - 145, panelY + 10, 0xFFD8C7FF);

        if (showHistory) {
            List<EliteCoinManager.Transaction> transactions = EliteCoinManager.transactions();
            if (transactions.isEmpty()) {
                g.text(font, "No transactions yet.", cx - 145, panelY + 31, 0xFF68737E);
            } else {
                for (int i = 0; i < Math.min(7, transactions.size()); i++) {
                    EliteCoinManager.Transaction t = transactions.get(i);
                    int y = panelY + 31 + i * 22;
                    String type = t.type().replace('_', ' ');
                    String amount = (t.coins() >= 0 ? "+" : "") + t.coins() + " EC";
                    g.text(font, type, cx - 145, y, 0xFFFFFFFF);
                    g.text(font, amount, cx + 72, y, t.coins() >= 0 ? 0xFF72E6A6 : 0xFFFF8A8A);
                }
            }
        }

        if (!showHistory) {
            List<EliteCoinManager.Mail> mail = EliteCoinManager.mailbox();
            if (mail.isEmpty()) {
                g.text(font, "No messages.", cx - 145, panelY + 31, 0xFF68737E);
            } else {
                for (int i = 0; i < Math.min(6, mail.size()); i++) {
                    EliteCoinManager.Mail m = mail.get(i);
                    int y = panelY + 30 + i * 28;
                    String sender = m.fromName() == null || m.fromName().isBlank() ? "Player" : m.fromName();
                    String line = sender + "  +" + m.coins() + " EC";
                    g.text(font, line, cx - 145, y, 0xFFFFFFFF);
                    if (!m.message().isBlank()) {
                        String message = m.message().length() > 38
                                ? m.message().substring(0, 38) + "..."
                                : m.message();
                        g.text(font, message, cx - 145, y + 11, 0xFF8E9AA6);
                    }
                    if ("UNREAD".equalsIgnoreCase(m.status())) {
                        g.text(font, "NEW", cx + 62, y, 0xFFFFD45A);
                    }
                }
            }
        }

        String bottomStatus = status;
        if (activitySession != null) {
            long seconds = Math.max(0, (activityEndsAt - System.currentTimeMillis() + 999) / 1000);
            bottomStatus = "Activity reward: " + seconds + "s remaining";
        }

        if (showHistory) {
            // History mode does not expose mailbox action buttons.
        }

        g.centeredText(font, Component.literal(bottomStatus),
                cx, height - 48, 0xFF8E9AA6);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
