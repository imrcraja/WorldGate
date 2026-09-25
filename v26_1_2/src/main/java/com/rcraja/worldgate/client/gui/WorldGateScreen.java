package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.WorldGateSkinCache;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.LanDiscovery;
import com.rcraja.worldgate.network.RelayBridge;
import com.rcraja.worldgate.client.WorldGateSounds;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class WorldGateScreen extends Screen {

    private final Screen parent;
    private EditBox roomCodeBox;
    private final Map<String, Boolean> knownPlayers = new HashMap<>();
    private boolean roomSnapshotInitialized = false;
    private volatile String roomJson = null;
    private boolean hostingRoom = false;

    public WorldGateScreen(Screen parent) {
        super(Component.translatable("worldgate.screen.title"));
        this.parent = parent;
        WorldGateSounds.play(WorldGateSounds.WORLDGATE_OPEN, 0.85F);
        WorldGateModClient.syncCurrentSkin();
    }

    @Override
    protected void init() {
        int margin = Math.max(10, Math.min(22, width / 30));
        int gap = 8;
        int top = 48;
        int footerY = height - 28;
        int cardW = Math.max(150, (width - margin * 2 - gap) / 2);
        int left = margin;
        int right = margin + cardW + gap;

        roomCodeBox = new EditBox(font, left + 8, top + 34, Math.max(110, cardW - 94), 20,
                Component.translatable("worldgate.roomcode.hint"));
        roomCodeBox.setMaxLength(10);
        roomCodeBox.setHint(Component.literal("Room code"));
        addRenderableWidget(roomCodeBox);
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.join"), b -> onJoin())
                .bounds(left + cardW - 78, top + 34, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.create"), b -> onCreate())
                .bounds(left + 8, top + 60, cardW - 16, 20).build());

        String[] labels = {
                "Profile", "Friends", "Chat", "Lobby",
                "Claim Center", "Settings", "Elite Store", "Cosmetics",
                "Emotes", "Account Security", "Discord", "Report"
        };
        int rows = (labels.length + 1) / 2;
        int startY = top + 96;
        int rowH = Math.max(24, Math.min(30, (height - startY - 42) / Math.max(1, rows)));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            int col = i % 2;
            int row = i / 2;
            int x = col == 0 ? left : right;
            int y = startY + row * rowH;
            Button button = Button.builder(Component.literal(labels[i]), b -> openDashboardAction(index))
                    .bounds(x, y, cardW, Math.min(24, rowH - 2)).build();
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.youtube"),
                b -> openLink(Constants.YOUTUBE_CHANNEL)).bounds(left, footerY, cardW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),
                b -> goBack()).bounds(right, footerY, cardW, 20).build());
    }

    private void openDashboardAction(int index) {
        if (minecraft == null) return;
        switch (index) {
            case 0 -> minecraft.setScreen(new EliteProfileScreen(this));
            case 1 -> minecraft.setScreen(new FriendsScreen(this));
            case 2 -> {
                String room = WorldGateModClient.CURRENT_ROOM_CODE;
                if (room != null && !room.isBlank()) minecraft.setScreen(new ChatScreen(this, room));
                else sendMessage("Join or create a room first.");
            }
            case 3 -> minecraft.setScreen(new LobbyScreen(this));
            case 4 -> minecraft.setScreen(new ClaimCenterScreen(this));
            case 5 -> minecraft.setScreen(new SettingsScreen(this));
            case 6 -> minecraft.setScreen(new EliteCoinScreen(this));
            case 7 -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS));
            case 8 -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.EMOTES));
            case 9 -> minecraft.setScreen(new AccountSecurityScreen(this));
            case 10 -> minecraft.setScreen(new DiscordLinkScreen(this));
            case 11 -> openLink(Constants.GITHUB_ISSUES);
            default -> { }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, 0xF2080B10);
        g.centeredText(font, Component.translatable("worldgate.screen.title"), width / 2, 16, 0xFFF2F6FA);
        g.centeredText(font, Component.literal("Social • Multiplayer • Elite"), width / 2, 31, 0xFF778697);

        int margin = Math.max(10, Math.min(22, width / 30));
        int gap = 8;
        int top = 48;
        int cardW = Math.max(150, (width - margin * 2 - gap) / 2);
        int left = margin;
        int right = margin + cardW + gap;
        g.fill(left, top, left + cardW, top + 82, 0xE50D1219);
        g.fill(right, top, right + cardW, top + 82, 0xE50D1219);
        g.outline(left, top, cardW, 82, 0xFF263341);
        g.outline(right, top, cardW, 82, 0xFF263341);
        g.text(font, Component.literal("ROOM"), left + 8, top + 9, 0xFF7DE2FF);
        g.text(font, Component.literal("SOCIAL"), right + 8, top + 9, 0xFFBFA7FF);
        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        g.text(font, Component.literal(room == null || room.isBlank() ? "No active room" : "Room " + room),
                right + 8, top + 30, 0xFFD8DDE3);
        g.text(font, Component.literal("Join/create a room, then use the dashboard below."),
                right + 8, top + 48, 0xFF718091);
        if (WorldGateModClient.HOSTING_ROOM_CODE != null && !WorldGateModClient.HOSTING_ROOM_CODE.isBlank()) {
            g.text(font, Component.literal("Hosting " + WorldGateModClient.HOSTING_ROOM_CODE),
                    left + 8, top + 87, 0xFF70E090);
        }
    }

    private void onCreate() {
        String activeHostRoom = WorldGateModClient.HOSTING_ROOM_CODE;
        if (activeHostRoom != null && !activeHostRoom.isBlank()) { roomCodeBox.setValue(activeHostRoom); sendMessage("WorldGate: already hosting Room Code = " + activeHostRoom); return; }
        if (minecraft == null || minecraft.player == null) return;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) { sendMessage("WorldGate: no singleplayer world is running."); return; }
        sendMessage("WorldGate: creating room...");
        if (server.isPublished()) { int existingPort = server.getPort(); if (existingPort <= 0 || existingPort > 65535) { sendMessage("WorldGate: existing published server has an invalid port."); return; } startWorldGateHost(server, existingPort); return; }
        int port = findFreePort();
        if (port <= 0) { sendMessage("WorldGate: could not find a free network port."); return; }
        sendMessage("WorldGate: using port " + port);
        boolean published = server.publishServer(GameType.SURVIVAL, true, port);
        if (!published) { sendMessage("WorldGate: failed to publish the world on port " + port); return; }
        int publishedPort = server.getPort();
        if (publishedPort <= 0 || publishedPort > 65535) { sendMessage("WorldGate: Minecraft returned an invalid published port."); return; }
        startWorldGateHost(server, publishedPort);
    }

    private static int findFreePort() { try (ServerSocket socket = new ServerSocket(0)) { socket.setReuseAddress(true); return socket.getLocalPort(); } catch (IOException e) { return -1; } }

    private void startWorldGateHost(IntegratedServer server, int port) {
        if (!HostBridge.start(port)) { sendMessage("WorldGate: failed to start host bridge on port " + port); return; }
        sendMessage("WorldGate: Minecraft server published on port " + port);
        WorldGateModClient.EXECUTOR.submit(() -> {
            String hostAddress = HostBridge.getAdvertiseAddress();
            String code = WorldGateModClient.ROOM_MANAGER.createRoom(hostAddress, port);
            if (code == null || code.isBlank()) { HostBridge.stop(); minecraft.execute(() -> sendMessage("WorldGate: failed to create room.")); return; }
            String hostName = minecraft.getUser().getName();
            WorldGateModClient.ROOM_MANAGER.playerJoin(code, hostName);
            HostBridge.startLanDiscovery(code, hostName);
            boolean relayStarted = WorldGateModClient.useInternetRelay() && HostBridge.startRelay(code);
            boolean localOnly = "lan".equals(WorldGateModClient.getNetworkMode());
            hostingRoom = true;
            WorldGateModClient.CURRENT_ROOM_CODE = code;
            WorldGateModClient.HOSTING_ROOM_CODE = code;
            WorldGateModClient.startHeartbeat(true);
            WorldGateSkinCache.refreshRoomPlayers(WorldGateModClient.ROOM_MANAGER.getRoom(code));
            minecraft.execute(() -> {
                if (localOnly) sendMessage("WorldGate: LAN-only room ready. No Internet data is required for discovery or Minecraft traffic.");
                else if (!relayStarted) sendMessage("WorldGate: relay could not start; LAN fallback is available.");
                WorldGateSounds.play(WorldGateSounds.WORLD_CONNECT, 0.9F);
                sendMessage("WorldGate: Room Code = " + code);
                startRoomListeners(code);
            });
        });
    }

    public void joinRoomFromInvite(String code) { if (code == null || code.isBlank()) { sendMessage("WorldGate: invite has no room code."); return; } roomCodeBox.setValue(code.trim().toUpperCase()); onJoin(); }

    private void onJoin() {
        String code = roomCodeBox.getValue().trim().replaceAll("\\D", "");
        roomCodeBox.setValue(code);
        if (code.isEmpty()) return;
        sendMessage("WorldGate: joining...");
        WorldGateModClient.EXECUTOR.submit(() -> {
            LanDiscovery.HostInfo lanHost = LanDiscovery.discover(code, 900);
            String roomJson = null;
            if (lanHost == null) roomJson = WorldGateModClient.ROOM_MANAGER.getRoom(code);
            final String discoveredRoomJson = roomJson;
            final LanDiscovery.HostInfo discoveredLanHost = lanHost;
            minecraft.execute(() -> {
                boolean lanDirect = discoveredLanHost != null;
                if (!lanDirect && (discoveredRoomJson == null || discoveredRoomJson.equals("null"))) { sendMessage("WorldGate: room not found."); return; }
                String hostAddress = lanDirect ? discoveredLanHost.address() : extractJsonString(discoveredRoomJson, "hostAddress");
                int hostPort = lanDirect ? discoveredLanHost.port() : extractJsonInt(discoveredRoomJson, "hostPort");
                if (hostAddress == null || hostAddress.isBlank() || hostPort <= 0 || hostPort > 65535) { sendMessage("WorldGate: invalid host address."); return; }
                String ign = minecraft.getUser().getName();
                boolean joined = lanDirect || WorldGateModClient.ROOM_MANAGER.playerJoin(code, ign);
                if (!joined) { minecraft.execute(() -> sendMessage("WorldGate: could not register you in the room.")); return; }
                int relayPort = WorldGateModClient.useInternetRelay() && !lanDirect ? RelayBridge.startPlayer(code) : -1;
                minecraft.execute(() -> {
                    hostingRoom = false;
                    WorldGateModClient.CURRENT_ROOM_CODE = code;
                    WorldGateModClient.startHeartbeat(false);
                    WorldGateSkinCache.refreshRoomPlayers(discoveredRoomJson);
                    ServerAddress address;
                    if (relayPort > 0) address = new ServerAddress("127.0.0.1", relayPort);
                    else if (WorldGateModClient.allowLanFallback() || lanDirect) address = new ServerAddress(hostAddress, hostPort);
                    else { sendMessage("WorldGate: Internet relay unavailable and LAN fallback is disabled."); WorldGateModClient.ROOM_MANAGER.playerLeave(code); return; }
                    ServerData serverData = new ServerData("WorldGate " + code, address.toString(), ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(this, minecraft, address, serverData, false, null);
                });
            });
        });
    }

    @Override public void onClose() { goBack(); }
    private void startRoomListeners(String code) { roomSnapshotInitialized=false; knownPlayers.clear(); roomJson=null; WorldGateModClient.ROOM_MANAGER.setRoomChangedListener(json -> { roomJson=json; handleRoomChanged(json); }); WorldGateModClient.ROOM_MANAGER.startRealtime(code); WorldGateModClient.EMOTE_MANAGER.listen(code,(uid,emote)->minecraft.execute(()->sendMessage(playerName(uid)+" "+emote))); }
    private void handleRoomChanged(String roomJson) { if(roomJson==null||roomJson.equals("null"))return; try { JsonObject room=JsonParser.parseString(roomJson).getAsJsonObject(); if(!room.has("players")||!room.get("players").isJsonObject())return; JsonObject players=room.getAsJsonObject("players"); Map<String,Boolean> current=new HashMap<>(); for(String uid:players.keySet()){JsonObject player=players.getAsJsonObject(uid); boolean online=player.has("online")&&player.get("online").getAsBoolean(); current.put(uid,online);} if(!roomSnapshotInitialized){knownPlayers.clear();knownPlayers.putAll(current);roomSnapshotInitialized=true;return;} for(String uid:current.keySet()){boolean online=current.get(uid),wasOnline=knownPlayers.getOrDefault(uid,false);if(online&&!wasOnline){String name=playerName(players,uid);minecraft.execute(()->sendMessage("WorldGate: "+name+" joined the room."));}} for(String uid:knownPlayers.keySet()){boolean wasOnline=knownPlayers.get(uid),online=current.getOrDefault(uid,false);if(wasOnline&&!online){String name=playerName(players,uid);minecraft.execute(()->sendMessage("WorldGate: "+name+" left the room."));}} knownPlayers.clear();knownPlayers.putAll(current);}catch(Exception e){Constants.class.getName();} }
    private String playerName(String uid){if(uid==null||uid.isBlank())return "Player";String currentUid=WorldGateModClient.FRIEND_MANAGER.myUid();if(uid.equals(currentUid))return "You";String name=playerNameFromRoomJson(roomJson,uid);return name!=null&&!name.isBlank()?name:"Player";}
    private static String playerName(JsonObject players,String uid){try{if(players!=null&&players.has(uid)&&players.get(uid).isJsonObject()){JsonObject player=players.getAsJsonObject(uid);if(player.has("ign")){String ign=player.get("ign").getAsString();if(ign!=null&&!ign.isBlank())return ign;}}}catch(Exception ignored){}return "Player";}
    private static String playerNameFromRoomJson(String json,String uid){if(json==null||json.isBlank()||"null".equals(json)||uid==null)return null;try{JsonObject room=JsonParser.parseString(json).getAsJsonObject();if(!room.has("players")||!room.get("players").isJsonObject())return null;JsonObject players=room.getAsJsonObject("players");if(!players.has(uid)||!players.get(uid).isJsonObject())return null;JsonObject player=players.getAsJsonObject(uid);if(player.has("ign")){String ign=player.get("ign").getAsString();if(ign!=null&&!ign.isBlank())return ign;}}catch(Exception ignored){}return null;}
    private static String extractJsonString(String json,String key){if(json==null||key==null)return null;try{JsonObject object=JsonParser.parseString(json).getAsJsonObject();if(!object.has(key)||object.get(key).isJsonNull())return null;return object.get(key).getAsString();}catch(Exception ignored){return null;}}
    private static int extractJsonInt(String json,String key){if(json==null||key==null)return -1;try{JsonObject object=JsonParser.parseString(json).getAsJsonObject();if(!object.has(key)||object.get(key).isJsonNull())return -1;return object.get(key).getAsInt();}catch(Exception ignored){return -1;}}
    private void sendMessage(String message){if(minecraft!=null&&minecraft.player!=null)minecraft.player.sendSystemMessage(Component.literal(message));else if(minecraft!=null)minecraft.setScreen(new WorldGateScreenWithMessage(parent,message));}
    private void openLink(String url){if(minecraft==null)return;minecraft.setScreen(new ConfirmLinkScreen(confirmed->{if(confirmed){try{Util.getPlatform().openUri(url);}catch(Exception ignored){}}minecraft.setScreen(this);},url,true));}
    private void stopWorldGateRealtime(){try{WorldGateModClient.ROOM_MANAGER.stopRealtime();}catch(Exception ignored){} try{WorldGateModClient.CHAT_MANAGER.stopListening();}catch(Exception ignored){} try{WorldGateModClient.EMOTE_MANAGER.stopListening();}catch(Exception ignored){}}
    private void goBack(){WorldGateSounds.play(WorldGateSounds.WORLDGATE_CLOSE,0.8F);stopWorldGateRealtime();if(minecraft!=null)minecraft.setScreen(parent);}
    private static class WorldGateScreenWithMessage extends Screen { private final Screen parent; private final String message; protected WorldGateScreenWithMessage(Screen parent,String message){super(Component.translatable("worldgate.screen.title"));this.parent=parent;this.message=message;} @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),btn->this.minecraft.setScreen(parent)).bounds(this.width/2-100,this.height/2+30,200,20).build());} @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){super.extractRenderState(graphics,mouseX,mouseY,delta);graphics.centeredText(this.font,Component.translatable("worldgate.screen.title"),this.width/2,this.height/2-25,0xFFFFFF);graphics.centeredText(this.font,Component.literal(message),this.width/2,this.height/2,0xFFFFFF);}}
}