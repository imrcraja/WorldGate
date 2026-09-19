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

/**
 * Shared client entrypoint and shared Firebase session -- identical on
 * every Minecraft version. Only the GUI screens and mixins that touch
 * actual Minecraft classes need a per-version copy (see each v*_*_*
 * module).
 */
public class WorldGateModClient implements ClientModInitializer {

    public static final FirebaseSession SESSION = new FirebaseSession();
    public static final RoomManager ROOM_MANAGER = new RoomManager(SESSION);
    public static final FriendManager FRIEND_MANAGER = new FriendManager(SESSION);
    public static final ChatManager CHAT_MANAGER = new ChatManager(SESSION);
    public static final EmoteManager EMOTE_MANAGER = new EmoteManager(SESSION);

    /** Firebase calls block on HTTP, so every button click hands off to this. */
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "WorldGate-Worker");
        t.setDaemon(true);
        return t;
    });

    /** Set once Create/Join succeeds; null until then. Read by the emote/chat UI. */
    public static volatile String CURRENT_ROOM_CODE = null;

    @Override
    public void onInitializeClient() {
        WorldGateMod.LOGGER.info("WorldGate client initialized.");
        // Sign in anonymously in the background so it's ready by the time
        // the player opens the WorldGate screen -- no need to block startup.
        EXECUTOR.submit(() -> {
            boolean ok = SESSION.connect();
            if (ok) {
                WorldGateMod.LOGGER.info("WorldGate Firebase session ready (uid={})", SESSION.uid());
            } else {
                WorldGateMod.LOGGER.error("WorldGate could not sign in to Firebase -- check internet/API key.");
            }
        });
    }
}
