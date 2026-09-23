package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.ChatManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatScreen extends Screen {
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);

    private final Screen parent;
    private final String roomCode;
    private final List<ChatLine> messages = new ArrayList<>();

    private EditBox input;
    private ChatLine selected;
    private String pendingUrl;

    public ChatScreen(Screen parent, String roomCode) {
        super(Component.literal("WorldGate Chat"));
        this.parent = parent;
        this.roomCode = roomCode;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        input = new EditBox(
                font, cx - 150, height - 48, 220, 20,
                Component.literal("Message")
        );
        input.setMaxLength(200);
        addRenderableWidget(input);

        addRenderableWidget(Button.builder(
                Component.literal("Send"),
                b -> send()
        ).bounds(cx + 75, height - 48, 75, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Reply"),
                b -> reply()
        ).bounds(cx - 150, height - 24, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Delete"),
                b -> deleteSelected()
        ).bounds(cx - 75, height - 24, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Open Link"),
                b -> openPendingUrl()
        ).bounds(cx, height - 24, 85, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Back"),
                b -> closeScreen()
        ).bounds(cx + 90, height - 24, 60, 20).build());

        WorldGateModClient.CHAT_MANAGER.listenDetailed(roomCode, event ->
                Minecraft.getInstance().execute(() -> applyEvent(event)));
    }

    private void applyEvent(ChatManager.ChatEvent event) {
        if (event.deleted()) {
            for (int i = 0; i < messages.size(); i++) {
                ChatLine line = messages.get(i);
                if (line.id().equals(event.messageId())) {
                    messages.set(i, new ChatLine(
                            line.id(), line.senderUid(), "[deleted]",
                            line.replyTo(), true));
                    break;
                }
            }
            return;
        }

        messages.add(new ChatLine(
                event.messageId(),
                event.senderUid(),
                event.text(),
                event.replyTo(),
                false));

        while (messages.size() > 80) {
            messages.remove(0);
        }
    }

    private void send() {
        if (input == null) return;
        String text = input.getValue().trim();
        if (text.isEmpty()) return;

        WorldGateModClient.EXECUTOR.submit(() ->
                WorldGateModClient.CHAT_MANAGER.sendMessage(roomCode, text));
        input.setValue("");
    }

    private void reply() {
        if (selected == null || input == null) return;
        input.setValue("@reply " + selected.text() + " ");
        input.setFocused(true);
    }

    private void deleteSelected() {
        if (selected == null) return;

        String myUid = WorldGateModClient.FRIEND_MANAGER.myUid();
        if (myUid == null || !myUid.equals(selected.senderUid())) return;

        String id = selected.id();
        WorldGateModClient.EXECUTOR.submit(() ->
                WorldGateModClient.CHAT_MANAGER.deleteMessage(roomCode, id));
    }

    private void openPendingUrl() {
        if (pendingUrl == null || minecraft == null) return;

        String url = pendingUrl;
        minecraft.setScreen(new ConfirmLinkScreen(
                confirmed -> {
                    if (confirmed) {
                        try {
                            net.minecraft.util.Util.getPlatform().openUri(url);
                        } catch (Exception ignored) {
                        }
                    }
                    minecraft.setScreen(this);
                },
                url,
                true
        ));
    }

    private void select(ChatLine line) {
        selected = line;
        pendingUrl = findUrl(line.text());
    }

    private static String findUrl(String text) {
        if (text == null) return null;
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private void closeScreen() {
        WorldGateModClient.CHAT_MANAGER.stopListening();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = width / 2;
        graphics.centeredText(
                font,
                Component.literal("WorldGate Chat • Room " + roomCode),
                cx,
                18,
                0xFFFFFF
        );

        int first = Math.max(0, messages.size() - 12);
        int y = 42;

        for (int i = first; i < messages.size(); i++) {
            ChatLine line = messages.get(i);
            String marker = line == selected ? "> " : "  ";
            String reply = line.replyTo().isBlank() ? "" : " ↪ ";
            String text = marker + line.senderUid() + reply + line.text();

            graphics.centeredText(
                    font,
                    Component.literal(text),
                    cx,
                    y,
                    line.deleted() ? 0x777777 : 0xFFFFFF
            );
            y += 16;
        }

        if (selected != null) {
            graphics.centeredText(
                    font,
                    Component.literal("Selected: " + selected.text()),
                    cx,
                    height - 72,
                    0xFFFFFF
            );
        }
    }

    private record ChatLine(
            String id,
            String senderUid,
            String text,
            String replyTo,
            boolean deleted
    ) {}
}
