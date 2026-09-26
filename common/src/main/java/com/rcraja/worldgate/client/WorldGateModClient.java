package com.rcraja.worldgate.client;

import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.network.ChatManager;
import com.rcraja.worldgate.network.EmoteManager;
import com.rcraja.worldgate.network.FirebaseSession;
import com.rcraja.worldgate.network.FriendManager;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;
import com.rcraja.worldgate.network.RoomManager;
import com.rcraja.worldgate.network.VoiceManager;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.players.NameAndId;

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
    public static final VoiceManager VOICE_MANAGER = new VoiceManager(SESSION);

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
    public static void stopVoice(){ VOICE_MANAGER.stop(); }\n    public static boolean startVoice(){ String room=CURRENT_ROOM_CODE; if(room==null||room.isBlank())return false; String role=HOSTING_ROOM_CODE!=null&&HOSTING_ROOM_CODE.equals(room)?"host":"player"; return VOICE_MANAGER.start(room,role); }\n\n    public static void leaveCurrentRoomOnWorldDisconnect() {
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
        VOICE_MANAGER.stop();
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

    private static final ScheduledExecutorService HOST_PERMISSION_SYNC =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WorldGate-Host-Permissions");
                t.setDaemon(true);
                return t;
            });

    private static volatile boolean hostPermissionSyncStarted = false;

    private static synchronized void startHostPermissionSync() {
        if (hostPermissionSyncStarted) return;
        hostPermissionSyncStarted = true;
        HOST_PERMISSION_SYNC.scheduleAtFixedRate(() -> {
            try {
                String room = HOSTING_ROOM_CODE;
                if (room == null || room.isBlank()) return;

                Minecraft mc = Minecraft.getInstance();
                IntegratedServer server = mc.getSingleplayerServer();
                if (server == null || !server.isPublished()) return;

                for (var player : server.getPlayerList().getPlayers()) {
                    if (!HostPermissionManager.contains(player.getUUID())) continue;
                    if (!server.getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
                        server.getPlayerList().op(new NameAndId(player.getGameProfile()));
                    }
                }
            } catch (Exception e) {
                WorldGateMod.LOGGER.debug("Host OP sync failed", e);
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

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
        HostPermissionManager.load();
        startHostPermissionSync();
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
                WorldGateOnlineSession.start();
                String displayName = Minecraft.getInstance().getUser().getName();
                FRIEND_MANAGER.setOnline(displayName);
                syncCurrentSkin();

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

    /**
     * Sync the skin the local player is actually rendering. This is deliberately
     * callable after entering a world because cracked/offline launchers may only
     * expose their skin after the player object has been created.
     */
    public static void syncCurrentSkin() {
        EXECUTOR.submit(() -> {
            if (!SESSION.isReady()) return;

            try {
                String skinUrl = null;
                Minecraft minecraft = Minecraft.getInstance();
                Object player = minecraft.player;

                if (player != null) {
                    Object skin = player.getClass().getMethod("getSkin").invoke(player);
                    if (skin != null) {
                        Object value = skin.getClass().getMethod("textureUrl").invoke(skin);
                        if (value != null) skinUrl = String.valueOf(value);
                    }
                }

                if (skinUrl == null || skinUrl.isBlank()) {
                    Object skin = minecraft.getSkinManager()
                            .getClass()
                            .getMethod("getInsecureSkin", com.mojang.authlib.GameProfile.class)
                            .invoke(minecraft.getSkinManager(), minecraft.getGameProfile());
                    if (skin != null) {
                        Object value = skin.getClass().getMethod("textureUrl").invoke(skin);
                        if (value != null) skinUrl = String.valueOf(value);
                    }
                }

                if (skinUrl != null && !skinUrl.isBlank()) {
                    FRIEND_MANAGER.updateMySkin(skinUrl);
                    WorldGateMod.LOGGER.info("WorldGate synced current player skin.");
                } else {
                    WorldGateMod.LOGGER.debug("WorldGate could not resolve a public skin URL for the current player.");
                }
            } catch (Exception e) {
                WorldGateMod.LOGGER.debug("WorldGate skin sync unavailable", e);
            }
        });
    }
}
