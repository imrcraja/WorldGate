package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.WorldGateOnlineSession;
import com.rcraja.worldgate.network.BackendClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AccountSecurityScreen extends Screen {
    private final Screen parent;
    private String status="Loading sessions...";
    private JsonArray sessions=new JsonArray();
    private static final int MAX_VISIBLE_SESSIONS=12;
    private final Button[] revokeButtons=new Button[MAX_VISIBLE_SESSIONS];
    private final String[] revokeIds=new String[MAX_VISIBLE_SESSIONS];

    public AccountSecurityScreen(Screen parent){super(Component.literal("Account Security"));this.parent=parent;}

    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->load())
                .bounds(width/2-110,70,220,20).build());
        for(int i=0;i<revokeButtons.length;i++){
            final int slot=i;
            revokeButtons[i]=addRenderableWidget(Button.builder(Component.literal("Revoke"),b->{
                String id=revokeIds[slot];
                if(id!=null&&!id.isBlank()) revoke(id);
            }).bounds(width/2+105,96+i*28,85,20).build());
        }
        updateRevokeButtons();
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose())
                .bounds(width/2-110,height-30,220,20).build());
        load();
    }

    private void load(){
        status="Loading sessions...";
        WorldGateModClient.EXECUTOR.submit(()->{
            String raw=BackendClient.listOnlineSessions(WorldGateModClient.SESSION);
            JsonArray found=new JsonArray();
            if(raw!=null)try{
                JsonObject root=JsonParser.parseString(raw).getAsJsonObject();
                if(root.has("sessions")&&root.get("sessions").isJsonArray())found=root.getAsJsonArray("sessions");
            }catch(Exception ignored){}
            final JsonArray result=found;
            if(minecraft!=null)minecraft.execute(()->{sessions=result;status=result.size()+" logged-in session(s)";updateRevokeButtons();});
        });
    }

    private void updateRevokeButtons(){
        for(int i=0;i<revokeButtons.length;i++){
            revokeIds[i]=null;
            if(i<sessions.size()){
                JsonObject item=sessions.get(i).getAsJsonObject();
                if(item.has("sessionId")) revokeIds[i]=item.get("sessionId").getAsString();
            }
            if(revokeButtons[i]!=null) revokeButtons[i].visible=revokeIds[i]!=null&&!revokeIds[i].isBlank();
        }
    }

    private void revoke(String id){
        status="Revoking session...";
        WorldGateModClient.EXECUTOR.submit(()->{
            String result=BackendClient.revokeOnlineSession(WorldGateModClient.SESSION,id);
            if(minecraft!=null)minecraft.execute(()->{status=result==null?"Could not revoke session.":"Session revoked.";load();});
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,"ACCOUNT SECURITY",width/2,24,0xFFFFFFFF);
        g.centeredText(font,"Logged-in devices and WorldGate online presence",width/2,42,0xFF9AA7B4);
        g.centeredText(font,status,width/2,58,0xFF7DE2FF);
        int y=100;
        for(int i=0;i<sessions.size();i++){
            JsonObject item=sessions.get(i).getAsJsonObject();
            String name=item.has("deviceName")?item.get("deviceName").getAsString():"WorldGate Device";
            boolean active=item.has("active")&&item.get("active").getAsBoolean();
            String id=item.has("sessionId")?item.get("sessionId").getAsString():"";
            boolean current=id.equals(WorldGateOnlineSession.id());
            String state=active?(current?"ONLINE • THIS DEVICE":"ONLINE"):"logged in";
            g.text(font,name+" — "+state,width/2-150,y,0xFFFFFFFF);
            if(!id.isBlank())g.text(font,"Session "+id.substring(0,Math.min(8,id.length()))+"…",width/2-150,y+14,0xFF8E9AA6);
            y+=28;
            if(y>height-55)break;
        }
    }

    @Override public void onClose(){minecraft.setScreen(parent);}
}
