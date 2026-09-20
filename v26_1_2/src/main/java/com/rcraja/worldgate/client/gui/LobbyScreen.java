package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LobbyScreen extends Screen {

    private final Screen parent;
    private EditBox skinUrlBox;

    public LobbyScreen(Screen parent) {
        super(Component.translatable("worldgate.lobby.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {

        int centerX = this.width / 2;
        int y = this.height / 2 - 55;

        this.skinUrlBox = new EditBox(
                this.font,
                centerX - 100,
                y,
                200,
                20,
                Component.translatable(
                        "worldgate.lobby.skin_hint"
                )
        );

        this.skinUrlBox.setMaxLength(256);

        this.skinUrlBox.setHint(
                Component.translatable(
                        "worldgate.lobby.skin_hint"
                )
        );

        this.addRenderableWidget(
                this.skinUrlBox
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable(
                                "worldgate.lobby.set_skin"
                        ),
                        btn -> showMessage(
                                Component.translatable(
                                        "worldgate.lobby.skin_todo"
                                )
                        )
                )
                .bounds(
                        centerX - 100,
                        y + 25,
                        200,
                        20
                )
                .build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable(
                                "worldgate.button.back"
                        ),
                        btn -> goBack()
                )
                .bounds(
                        centerX - 100,
                        y + 65,
                        200,
                        20
                )
                .build()
        );
    }

    private void showMessage(Component message) {

        if (this.minecraft != null) {
            this.minecraft.setScreen(
                    new LobbyMessageScreen(
                            this,
                            message
                    )
            );
        }
    }

    private void goBack() {

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {

        this.renderBackground(
                graphics,
                mouseX,
                mouseY,
                delta
        );

        int centerX = this.width / 2;

        graphics.drawCenteredString(
                this.font,
                "WorldGate Lobby",
                centerX,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                "Character",
                centerX,
                55,
                0x55FFFF
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                delta
        );
    }

    @Override
    public void onClose() {
        goBack();
    }

    private static class LobbyMessageScreen
            extends Screen {

        private final Screen parent;
        private final Component message;

        protected LobbyMessageScreen(
                Screen parent,
                Component message
        ) {
            super(
                    Component.literal("WorldGate")
            );

            this.parent = parent;
            this.message = message;
        }

        @Override
        protected void init() {

            int centerX = this.width / 2;

            this.addRenderableWidget(
                    Button.builder(
                            Component.literal("Back"),
                            btn -> this.minecraft.setScreen(
                                    parent
                            )
                    )
                    .bounds(
                            centerX - 100,
                            this.height / 2 + 25,
                            200,
                            20
                    )
                    .build()
            );
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float delta
        ) {

            this.renderBackground(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );

            graphics.drawCenteredString(
                    this.font,
                    message,
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );

            super.render(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );
        }
    }
}
