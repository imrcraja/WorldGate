package com.rcraja.worldgate.client;

import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.network.ChatManager;
import com.rcraja.worldgate.network.EmoteManager;
import com.rcraja.worldgate.network.FirebaseSession;
import com.rcraja.worldgate.network.FriendManager;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;
import com.rcraja.worldgate.network.RoomManager;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorldGateModClient implements ClientModInitializer {

    public static final FirebaseSession SESSION = new FirebaseSession();
    public static final RoomManager ROOM_MANAGER = new RoomManager(SESSION);
    public static final FriendManager FRIEND_MANAGER = new FriendManager(SESSION);
    public static final ChatManager CHAT_MANAGER = new ChatManager(SESSION);
    public static final EmoteManager EMOTE_MANAGER = new EmoteManager(SESSION);

    public static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "WorldGate-Worker");
                t.setDaemon(true);
                return t;
            });

    public static volatile String CURRENT_ROOM_CODE = null;
    /** Stable for the entire lifetime of the currently hosted Minecraft world. */
    public static volatile String HOSTING_ROOM_CODE = null;

    private static volatile String networkMode = "auto";

    public static String getNetworkMode() {
        return networkMode;
    }

    public static void setNetworkMode(String mode) {
        if (!"lan".equals(mode) && !"internet".equals(mode) && !"auto".equals(mode)) {
            mode = "auto";
        }
        networkMode = mode;
        LocalWorldGateData.set("networkMode", mode);
    }

    public static boolean useInternetRelay() {
        return !"lan".equals(networkMode);
    }

    public static boolean allowLanFallback() {
        return !"internet".equals(networkMode);
    }

    /**
     * Ends the WorldGate room only when the actual Minecraft world/network
     * connection is closing. Opening/closing WorldGate screens must not change
     * the room code or make the host leave.
     */
    public static void leaveCurrentRoomOnWorldDisconnect() {
        final String room = CURRENT_ROOM_CODE;
        final boolean host = HOSTING_ROOM_CODE != null
                && HOSTING_ROOM_CODE.equals(room);

        if (room == null || room.isBlank()) {
            RelayBridge.stop();
            HostBridge.stop();
            HOSTING_ROOM_CODE = null;
            CURRENT_ROOM_CODE = null;
            stopHeartbeat();
            return;
        }

        stopHeartbeat();
        HOSTING_ROOM_CODE = null;
        CURRENT_ROOM_CODE = null;

        EXECUTOR.submit(() -> {
            try {
                if (host) {
                    ROOM_MANAGER.hostLeave(room);
                    HostBridge.stop();
                } else {
                    ROOM_MANAGER.playerLeave(room);
                    RelayBridge.stop();
                }
            } catch (Exception e) {
                WorldGateMod.LOGGER.debug("WorldGate room cleanup failed", e);
                HostBridge.stop();
                RelayBridge.stop();
            }
        });
    }

    private static final ScheduledExecutorService HEARTBEAT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WorldGate-Heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static volatile boolean heartbeatRunning = false;
    private static java.util.concurrent.ScheduledFuture<?> heartbeatTask;

    public static synchronized void startHeartbeat(boolean host) {
        if (heartbeatRunning) {
            return;
        }

        heartbeatRunning = true;

        heartbeatTask = HEARTBEAT.scheduleAtFixedRate(
                () -> {
                    String room = CURRENT_ROOM_CODE;
                    if (room == null || room.isBlank()) {
                        return;
                    }

                    if (host) {
                        ROOM_MANAGER.hostHeartbeat(room);
                    } else {
                        ROOM_MANAGER.playerHeartbeat(room);
                    }
                },
                0,
                15,
                TimeUnit.SECONDS
        );
    }

    public static synchronized void stopHeartbeat() {
        heartbeatRunning = false;
        java.util.concurrent.ScheduledFuture<?> task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    @Override
    public void onInitializeClient() {
        WorldGateMod.LOGGER.info("WorldGate client initialized.");

        WorldGateSounds.initialize();
        LocalWorldGateData.load();
        networkMode = LocalWorldGateData.get("networkMode", "auto");
        registerVersionKeybinds();

        EXECUTOR.submit(() -> {
            boolean connected = SESSION.connect();

            Minecraft.getInstance().execute(() ->
                    WorldGateSounds.play(
                            connected ? WorldGateSounds.AUTH_SUCCESS : WorldGateSounds.AUTH_FAIL,
                            0.9F
                    )
            );

            if (connected) {
                String displayName = Minecraft.getInstance().getUser().getName();
                FRIEND_MANAGER.setOnline(displayName);
                try {
                    /*
                     * Prefer the skin actually attached to the local player.
                     * This matters for offline/cracked launchers: SkinManager's
                     * profile lookup can have no Mojang texture even though the
                     * local player is visibly using a custom skin.
                     */
                    String skinUrl = null;
                    try {
                        Object player = Minecraft.getInstance().player;
                        if (player != null) {
                            Object skin = player.getClass().getMethod("getSkin").invoke(player);
                            if (skin != null) {
                                Object value = skin.getClass().getMethod("textureUrl").invoke(skin);
                                if (value != null) skinUrl = String.valueOf(value);
                            }
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Fall back to the normal profile lookup below.
                    }

                    if (skinUrl == null || skinUrl.isBlank()) {
                        try {
                            Object skin = Minecraft.getInstance().getSkinManager()
                                    .getClass()
                                    .getMethod("getInsecureSkin", com.mojang.authlib.GameProfile.class)
                                    .invoke(Minecraft.getInstance().getSkinManager(),
                                            Minecraft.getInstance().getGameProfile());
                            if (skin != null) {
                                Object value = skin.getClass().getMethod("textureUrl").invoke(skin);
                                if (value != null) skinUrl = String.valueOf(value);
                            }
                        } catch (ReflectiveOperationException ignored) {
                            WorldGateMod.LOGGER.debug("WorldGate skin API shape changed; skipping automatic skin sync");
                        }
                    }

                    if (skinUrl != null && !skinUrl.isBlank()) {
                        FRIEND_MANAGER.updateMySkin(skinUrl);
                        WorldGateMod.LOGGER.info("WorldGate synced current player skin.");
                    }
                } catch (Exception e) {
                    WorldGateMod.LOGGER.debug("WorldGate skin sync unavailable", e);
                }

                WorldGateMod.LOGGER.info(
                        "WorldGate Firebase session ready (uid={})",
                        SESSION.uid()
                );
            } else {
                WorldGateMod.LOGGER.error(
                        "WorldGate could not sign in to Firebase -- check internet/API key."
                );
            }
        });
    }

    private static void registerVersionKeybinds() {
        try {
            Class<?> keybindClass =
                    Class.forName("com.rcraja.worldgate.client.WorldGateKeybinds");

            keybindClass.getMethod("register").invoke(null);

            WorldGateMod.LOGGER.info("WorldGate keybinds registered.");
        } catch (ClassNotFoundException ignored) {
            /*
             * A version module without the optional
             * keybind implementation simply continues.
             */
        } catch (Exception e) {
            WorldGateMod.LOGGER.error(
                    "WorldGate keybind registration failed.",
                    e
            );
        }
    }
}
