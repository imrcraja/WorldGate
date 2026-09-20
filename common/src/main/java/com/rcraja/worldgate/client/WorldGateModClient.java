package com.rcraja.worldgate.client;

import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.network.ChatManager;
import com.rcraja.worldgate.network.EmoteManager;
import com.rcraja.worldgate.network.FirebaseSession;
import com.rcraja.worldgate.network.FriendManager;
import com.rcraja.worldgate.network.RoomManager;

import net.fabricmc.api.ClientModInitializer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorldGateModClient implements ClientModInitializer {

    public static final FirebaseSession SESSION =
            new FirebaseSession();

    public static final RoomManager ROOM_MANAGER =
            new RoomManager(SESSION);

    public static final FriendManager FRIEND_MANAGER =
            new FriendManager(SESSION);

    public static final ChatManager CHAT_MANAGER =
            new ChatManager(SESSION);

    public static final EmoteManager EMOTE_MANAGER =
            new EmoteManager(SESSION);

    public static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "WorldGate-Worker");
                t.setDaemon(true);
                return t;
            });

    public static volatile String CURRENT_ROOM_CODE = null;

    private static final ScheduledExecutorService HEARTBEAT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WorldGate-Heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static volatile boolean heartbeatRunning = false;

    public static synchronized void startHeartbeat(boolean host) {
        if (heartbeatRunning) {
            return;
        }

        heartbeatRunning = true;

        HEARTBEAT.scheduleAtFixedRate(() -> {
            String room = CURRENT_ROOM_CODE;

            if (room == null) {
                return;
            }

            if (host) {
                ROOM_MANAGER.hostHeartbeat(room);
            } else {
                ROOM_MANAGER.playerHeartbeat(room);
            }

        }, 0, 15, TimeUnit.SECONDS);
    }

    public static synchronized void stopHeartbeat() {
        heartbeatRunning = false;
    }

    @Override
    public void onInitializeClient() {

        WorldGateMod.LOGGER.info(
                "WorldGate client initialized."
        );

        // Firebase login happens in background.
        EXECUTOR.submit(() -> {

            boolean ok = SESSION.connect();

            if (ok) {
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
}
