package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class ClaimCenterScreen extends Screen {
    private final Screen parent;
    private final String initialUid;
    private EditBox uidBox,amountBox,messageBox;
    private String status="";
    private long activityEndsAt=0;
    private String activitySession=null;
    public ClaimCenterScreen(Screen parent){this(parent,"");}
    public ClaimCenterScreen(Screen parent,String initialUid){super(Component.translatable("worldgate.claim.title"));this.parent=parent;this.initialUid=initialUid==null?"":initialUid;}
    @Override protected void init(){int cx=width/2;uidBox=new EditBox(font,cx-145,64,290,20,Component.translatable("worldgate.claim.uid"));uidBox.setMaxLength(128);uidBox.setValue(initialUid);addRenderableWidget(uidBox);amountBox=new EditBox(font,cx-145,89,70,20,Component.translatable("worldgate.claim.amount"));amountBox.setMaxLength(9);amountBox.setValue("10");addRenderableWidget(amountBox);messageBox=new EditBox(font,cx-65,89,210,20,Component.translatable("worldgate.claim.message"));messageBox.setMaxLength(300);addRenderableWidget(messageBox);addRenderableWidget(Button.builder(Component.translatable("worldgate.claim.daily"),b->claimDaily()).bounds(cx-145,120,92,20).build());addRenderableWidget(Button.builder(Component.translatable("worldgate.claim.activity"),b->startActivity()).bounds(cx-47,120,110,20).build());addRenderableWidget(Button.builder(Component.translatable("worldgate.claim.gift"),b->gift()).bounds(cx+69,120,76,20).build());addRenderableWidget(Button.builder(Component.translatable("worldgate.claim.refresh"),b->refresh()).bounds(cx-145,height-30,92,20).build());addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),b->onClose()).bounds(cx-47,height-30,192,20).build());refresh();}
    private void refresh(){status="Syncing...";EliteCoinManager.refresh(()->{if(minecraft!=null)minecraft.execute(()->status="Synced.");});}
    private void claimDaily(){WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.claimDaily();if(minecraft!=null)minecraft.execute(()->{status=r==null?"Claim failed.":r.contains("ALREADY_CLAIMED")?"Daily reward already claimed.":"Daily reward claimed.";refresh();});});}
    private void startActivity(){if(activitySession!=null)return;WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.startActivity();String id=null;try{id=JsonParser.parseString(r).getAsJsonObject().get("id").getAsString();}catch(Exception ignored){}final String sid=id;if(minecraft!=null)minecraft.execute(()->{if(sid==null){status="Activity reward unavailable.";return;}activitySession=sid;activityEndsAt=System.currentTimeMillis()+20000;status="Wait 20 seconds...";});});}
    private void gift(){String uid=uidBox.getValue().trim();long coins;try{coins=Long.parseLong(amountBox.getValue().trim());}catch(Exception e){status="Enter a valid coin amount.";return;}if(uid.isEmpty()||coins<=0){status="Enter recipient UID and amount.";return;}String msg=messageBox.getValue().trim();WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.gift(uid,coins,msg);if(minecraft!=null)minecraft.execute(()->{status=r!=null&&r.contains("\"ok\":true")?"Gift sent.":"Gift could not be sent.";refresh();});});}
    @Override public void tick(){super.tick();if(activitySession!=null&&System.currentTimeMillis()>=activityEndsAt){String id=activitySession;activitySession=null;WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.completeActivity(id);if(minecraft!=null)minecraft.execute(()->{status=r!=null&&r.contains("REWARDED")?"Activity reward claimed.":"Activity reward could not be claimed.";refresh();});});}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);int cx=width/2;g.centeredText(font,Component.translatable("worldgate.claim.title"),cx,18,0xFFFFFFFF);g.centeredText(font,Component.translatable("worldgate.coin.balance",Long.toString(EliteCoinManager.wallet().balance())),cx,40,0xFFFFD45A);g.text(font,"Recipient UID",cx-145,54,0xFFAAAAAA);g.text(font,"Coins",cx-145,79,0xFFAAAAAA);g.text(font,"Message (optional)",cx-65,79,0xFFAAAAAA);int y=165;g.text(font,"Mailbox",cx-145,y,0xFFD8C7FF);int row=y+18;List<EliteCoinManager.Mail> mail=EliteCoinManager.mailbox();if(mail.isEmpty())g.text(font,"No messages.",cx-145,row,0xFF888888);else for(EliteCoinManager.Mail m:mail.stream().limit(6).toList()){String line=m.fromName()+"  +"+m.coins()+" EC";g.text(font,line,cx-145,row,0xFFFFFFFF);if(!m.message().isBlank())g.text(font,m.message(),cx-145,row+11,0xFFAAAAAA);if("UNREAD".equalsIgnoreCase(m.status()))g.text(font,"NEW",cx+115,row,0xFFFF4040);row+=28;}g.centeredText(font,Component.translatable("worldgate.status.raw",status),cx,height-45,0xFF888888);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}