package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.UserProfileCache;
import com.rcraja.worldgate.client.elite.EliteBadgeRenderer;
import com.rcraja.worldgate.client.elite.EliteManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EliteProfileScreen extends Screen {
    private final Screen parent;
    private EliteManager.EliteProfile profile=EliteManager.EliteProfile.unavailable();
    private boolean loading=true;
    private String status="Loading Elite profile...";
    public EliteProfileScreen(Screen parent){super(Component.translatable("worldgate.elite.title"));this.parent=parent;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.shop"),b->minecraft.setScreen(new EliteCoinScreen(this))).bounds(width/2-155,height-55,97,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.refresh"),b->load()).bounds(width/2-52,height-55,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),b->minecraft.setScreen(parent)).bounds(width/2+56,height-55,99,20).build());
        load();
    }
    private void load(){loading=true;status="Syncing profile...";WorldGateModClient.EXECUTOR.submit(()->{
        EliteManager.refreshOwnProfile();
        if(WorldGateModClient.SESSION.isReady()){
            String uid=WorldGateModClient.SESSION.uid();
            String raw=WorldGateModClient.FRIEND_MANAGER.getProfile(uid);
            if(raw!=null && !raw.isBlank() && !"null".equals(raw)) UserProfileCache.save(raw);
            WorldGateModClient.FRIEND_MANAGER.myFriendCode();
        }
        EliteManager.EliteProfile loaded=EliteManager.loadOwnProfile();
        if(minecraft!=null)minecraft.execute(()->{
            profile=loaded;
            loading=false;
            status=loaded.available()?"Server-synced Elite profile":"Using local profile cache; Elite entitlement is unavailable right now.";
        });
    });}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        int cx=width/2;
        graphics.centeredText(font,Component.translatable("worldgate.elite.title"),cx,18,0xFFFFFFFF);
        graphics.centeredText(font,Component.translatable(profile.available()?"worldgate.elite.level":"worldgate.elite.profile",profile.available()?Integer.toString(profile.level()):""),cx,31,0xFFD8C7FF);

        String displayName=UserProfileCache.value("displayName","Player");
        String uid=WorldGateModClient.SESSION.uid();
        String publicId=UserProfileCache.value("publicId","Not set");
        graphics.centeredText(font,Component.literal(displayName),cx,52,0xFFFFFFFF);
        graphics.centeredText(font,Component.literal("Public ID: "+(publicId==null?"—":publicId)),cx,67,0xFF7DE2FF);
        

        if(profile.hasElite()){
            EliteBadgeRenderer.draw(graphics,font,cx,101,116,profile.level());
            int y=224;
            graphics.centeredText(font,Component.translatable("worldgate.elite.eligible_spending",money(profile.eligibleSpentMinorUnits())),cx,y,0xFFFFFFFF);
            if(profile.nextLevelThresholdMinorUnits()>0)graphics.centeredText(font,Component.translatable("worldgate.elite.remaining_next",money(profile.remainingToNext())),cx,y+16,0xFFBDBDBD);
            if(profile.maxLevelThresholdMinorUnits()>0)graphics.centeredText(font,Component.translatable("worldgate.elite.remaining_max",money(profile.remainingToMax())),cx,y+32,0xFFBDBDBD);
            graphics.centeredText(font,Component.translatable("worldgate.elite.badge_note"),cx,y+55,0xFF8F8F8F);
        }else{
            graphics.centeredText(font,Component.translatable(loading?"worldgate.loading":"worldgate.elite.not_published"),cx,106,0xFFAAAAAA);
        }
        graphics.centeredText(font,Component.translatable("worldgate.status.raw",status),cx,height-76,0xFF888888);
    }
    private static String money(long n){return String.format(java.util.Locale.ROOT,"USD %.2f",n/100.0);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}