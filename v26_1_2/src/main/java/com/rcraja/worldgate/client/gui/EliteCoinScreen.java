package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EliteCoinManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EliteCoinScreen extends Screen {
    private final Screen parent;
    private String status="";
    public EliteCoinScreen(Screen parent){super(Component.translatable("worldgate.coin.title"));this.parent=parent;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.refresh"),b->refresh()).bounds(width/2-155,height-55,97,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.buy"),b->requestPackage()).bounds(width/2-52,height-55,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),b->onClose()).bounds(width/2+56,height-55,99,20).build());
        refresh();
    }
    private void refresh(){status="Loading...";EliteCoinManager.refresh();WorldGateModClient.EXECUTOR.submit(()->{try{Thread.sleep(350);}catch(InterruptedException ignored){}if(minecraft!=null)minecraft.execute(()->status=EliteCoinManager.wallet().available()?"Server synced":"Coin data unavailable");});}
    private void requestPackage(){String r=EliteCoinManager.requestPackage("starter");status=r==null?"Payment gateway is not configured. No payment was charged.":(r.contains("GATEWAY_REQUIRED")?"Payment gateway is not configured. No payment was charged.":"Purchase response received.");}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);int cx=width/2;g.centeredText(font,Component.translatable("worldgate.coin.title"),cx,18,0xFFFFFFFF);g.centeredText(font,Component.translatable("worldgate.coin.balance",Long.toString(EliteCoinManager.wallet().balance())),cx,40,0xFFFFD45A);EliteCoinManager.Catalog c=EliteCoinManager.catalog();int y=70;g.centeredText(font,Component.translatable("worldgate.coin.catalog",c.name()),cx,y,0xFFD8C7FF);int row=0;for(EliteCoinManager.Item item:c.items()){int yy=y+24+row*28;g.text(font,item.name()+" — "+item.priceCoins()+" "+c.symbol(),30,yy,0xFFFFFFFF);g.text(font,item.description(),30,yy+11,0xFF999999);final String id=item.id();if(row<8)addRenderableWidget(Button.builder(Component.translatable("worldgate.coin.buy_item"),b->buy(id)).bounds(width-115,yy-4,95,20).build());row++;if(row>=8)break;}g.centeredText(font,Component.translatable("worldgate.status.raw",status),cx,height-75,0xFF888888);}
    private void buy(String id){status="Buying...";WorldGateModClient.EXECUTOR.submit(()->{String r=EliteCoinManager.purchaseItem(id);if(minecraft!=null)minecraft.execute(()->{if(r!=null&&r.contains(""ok":true")){status="Purchased successfully.";EliteCoinManager.refresh();}else if(r!=null&&r.contains("insufficient_balance"))status="Not enough Elite Coins.";else status=r==null?"Purchase failed.":"Purchase could not be completed.";});});}
    @Override public void onClose(){minecraft.setScreen(parent);}
}