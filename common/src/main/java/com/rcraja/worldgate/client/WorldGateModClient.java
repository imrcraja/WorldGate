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
                Thread thread =
                        new Thread(
                                r,
                                "WorldGate-Worker"
                        );

                thread.setDaemon(true);
                return thread;
            });

    public static volatile String CURRENT_ROOM_CODE =
            null;

    private static final ScheduledExecutorService HEARTBEAT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread =
                        new Thread(
                                r,
                                "WorldGate-Heartbeat"
                        );

                thread.setDaemon(true);
                return thread;
            });

    private static volatile boolean heartbeatRunning =
            false;

    private static volatile boolean heartbeatHost =
            false;

    public static synchronized void startHeartbeat(
            boolean host
    ) {

        if (CURRENT_ROOM_CODE == null
                || CURRENT_ROOM_CODE.isBlank()) {
            return;
        }

        /*
         * If the heartbeat is already running for the same
         * role, don't create another timer.
         */
        if (heartbeatRunning
                && heartbeatHost == host) {
            return;
        }

        heartbeatRunning = true;
        heartbeatHost = host;

        HEARTBEAT.scheduleAtFixedRate(
                () -> {

                    String room =
                            CURRENT_ROOM_CODE;

                    if (!heartbeatRunning
                            || room == null
                            || room.isBlank()) {
                        return;
                    }

                    if (heartbeatHost) {
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
    }

    public static synchronized void leaveCurrentRoom(
            boolean host
    ) {

        String room =
                CURRENT_ROOM_CODE;

        if (room == null
                || room.isBlank()) {
            stopHeartbeat();
            return;
        }

        stopHeartbeat();

        EXECUTOR.submit(() -> {

            try {

                if (host) {
                    ROOM_MANAGER.hostLeave(room);
                } else {
                    ROOM_MANAGER.playerLeave(room);
                }

            } finally {

                if (room.equals(
                        CURRENT_ROOM_CODE
                )) {
                    CURRENT_ROOM_CODE = null;
                }
            }
        });
    }

    @Override
    public void onInitializeClient() {

        WorldGateMod.LOGGER.info(
                "WorldGate client initialized."
        );

        EXECUTOR.submit(() -> {

            boolean connected =
                    SESSION.connect();

            if (connected) {

                WorldGateMod.LOGGER.info(
                        "WorldGate Firebase session ready (uid={})",
                        SESSION.uid()
                );

            } else {

                WorldGateMod.LOGGER.error(
                        "WorldGate could not sign in to Firebase -- "
                                + "check internet/API key."
                );
            }
        });
    }
    }
