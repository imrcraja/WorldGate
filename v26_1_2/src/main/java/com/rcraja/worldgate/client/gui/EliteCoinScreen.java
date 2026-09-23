package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public final class EliteCoinScreen extends Screen {
    private final Screen parent;
    private final List<Button> itemButtons=new ArrayList<>();
    private String status="";
    public EliteCoinScreen(Screen parent){super(Component.translatable("worldgate.coin.title"));this.parent=parent;}
    @Override protected void init(){
        for(int i=0;i<8;i++){final int index=i;Button b=Button.builder(Component.translatable("worldgate.coin.buy_item"),x->buyIndex(index)).bounds(width-115,94+i*28,95,20).build();b.active=false;itemButtons.add(b);addRenderableWidget(b);}
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.refresh"),b->refresh()).bounds(width/2-155,height-55,97,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.buy"),b->requestPackage()).bounds(width/2-52,height-55,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),b->onClose()).bounds(width/2+56,height-55,99,20).build());
        refresh();
    }
    private void refresh(){status="Loading...";for(Button b:itemButtons)b.active=false;EliteCoinManager.refresh();WorldGateModClient.EXECUTOR.submit(()->{try{Thread.sleep(400);}catch(InterruptedException ignored){}if(minecraft!=null)minecraft.execute(()->{var items=EliteCoinManager.catalog().items();for(int i=0;i<itemButtons.size();i++)itemButtons.get(i).active=i<items.size();status=EliteCoinManager.wallet().available()?"Server synced":"Coin data unavailable";});});}
    private void requestPackage(){String r=EliteCoinManager.requestPackage("starter");status=r==null?"Payment gateway is not configured. No payment was charged.":(r.contains("GATEWAY_REQUIRED")?"Payment gateway is not configured. No payment was charged.":"Purchase response received.");}
    private void buyIndex(int index){var items=EliteCoinManager.catalog().items();if(index<0||index>=items.size())return;String id=items.get(index).id();status="Buying...";WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.purchaseItem(id);if(minecraft!=null)minecraft.execute(()->{if(r!=null&&r.contains("\"ok\":true")){status="Purchased successfully.";EliteCoinManager.refresh();}else if(r!=null&&r.contains("insufficient_balance"))status="Not enough Elite Coins.";else status=r==null?"Purchase failed.":"Purchase could not be completed.";});});}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);int cx=width/2;g.centeredText(font,Component.translatable("worldgate.coin.title"),cx,18,0xFFFFFFFF);g.centeredText(font,Component.translatable("worldgate.coin.balance",Long.toString(EliteCoinManager.wallet().balance())),cx,40,0xFFFFD45A);var c=EliteCoinManager.catalog();g.centeredText(font,Component.translatable("worldgate.coin.catalog",c.name()),cx,70,0xFFD8C7FF);int row=0;for(var item:c.items()){if(row>=8)break;int yy=94+row*28;g.text(font,item.name()+" — "+item.priceCoins()+" "+c.symbol(),30,yy,0xFFFFFFFF);g.text(font,item.description(),30,yy+11,0xFF999999);row++;}if(c.items().isEmpty())g.centeredText(font,Component.translatable("worldgate.coin.no_items"),cx,100,0xFF888888);g.centeredText(font,Component.translatable("worldgate.status.raw",status),cx,height-75,0xFF888888);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}