package com.rcraja.worldgate.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

public final class EliteCoinManager {
    private static volatile Wallet wallet=Wallet.unavailable();
    private static volatile Catalog catalog=Catalog.empty();
    private static volatile List<Mail> mailbox=List.of();
    private static volatile RewardStatus rewardStatus=RewardStatus.empty();
    private static volatile Inventory inventory=Inventory.empty();
    private EliteCoinManager(){}
    public static void refresh(){refresh(null);}
    public static void refresh(Runnable done){FirebaseSession s=WorldGateModClient.SESSION;WorldGateModClient.EXECUTOR.submit(()->{try{wallet=parseWallet(BackendClient.coinWallet(s));catalog=parseCatalog(BackendClient.coinCatalog(s));mailbox=parseMailbox(BackendClient.coinMailbox(s));rewardStatus=parseRewardStatus(BackendClient.coinRewardStatus(s));inventory=parseInventory(BackendClient.coinInventory(s));}finally{if(done!=null)done.run();}});}
    public static Wallet wallet(){return wallet;}
    public static Catalog catalog(){return catalog;}
    public static List<Mail> mailbox(){return mailbox;}
    public static RewardStatus rewardStatus(){return rewardStatus;}
    public static Inventory inventory(){return inventory;}
    public static boolean hasNotification(){String today=LocalDate.now(ZoneOffset.UTC).toString();boolean daily=!today.equals(rewardStatus.lastClaimDate())&&rewardStatus.cycleCoins()<rewardStatus.maxCycleCoins();boolean activity=rewardStatus.activityEnabled()&&rewardStatus.activityUsedToday()<rewardStatus.activityDailyCap();boolean unread=mailbox.stream().anyMatch(m->"UNREAD".equalsIgnoreCase(m.status()));return daily||activity||unread;}
    public static String claimDaily(){return BackendClient.claimDaily(WorldGateModClient.SESSION);}
    public static String startActivity(){return BackendClient.startActivityReward(WorldGateModClient.SESSION);}
    public static String completeActivity(String id){return BackendClient.completeActivityReward(WorldGateModClient.SESSION,id);}
    public static String gift(String uid,long coins,String message){return BackendClient.giftCoins(WorldGateModClient.SESSION,uid,coins,message);}
    public static String markRead(String id){return BackendClient.markMailboxRead(WorldGateModClient.SESSION,id);}
    public static String purchaseItem(String id){return BackendClient.purchaseItem(WorldGateModClient.SESSION,id,UUID.randomUUID().toString());}
    public static String equipItem(String id){return BackendClient.equipItem(WorldGateModClient.SESSION,id);}
    public static String requestPackage(String id){return BackendClient.purchaseCoinPackage(WorldGateModClient.SESSION,id);}
    private static Wallet parseWallet(String r){try{JsonObject o=JsonParser.parseString(r==null?"{}":r).getAsJsonObject();return new Wallet(true,o.has("balance")?o.get("balance").getAsLong():0);}catch(Exception e){return Wallet.unavailable();}}
    private static Catalog parseCatalog(String r){try{JsonObject o=JsonParser.parseString(r==null?"{}":r).getAsJsonObject();JsonObject c=o.has("coin")?o.getAsJsonObject("coin"):o;List<Item> items=new ArrayList<>();JsonArray a=o.has("items")?o.getAsJsonArray("items"):new JsonArray();a.forEach(v->{JsonObject i=v.getAsJsonObject();items.add(new Item(str(i,"id"),str(i,"name"),str(i,"description"),i.has("priceCoins")?i.get("priceCoins").getAsLong():0));});return new Catalog(str(c,"name"),str(c,"symbol"),str(c,"iconPath"),items);}catch(Exception e){return Catalog.empty();}}
    private static List<Mail> parseMailbox(String r){try{JsonObject root=JsonParser.parseString(r==null?"{}":r).getAsJsonObject();JsonArray a=root.has("messages")?root.getAsJsonArray("messages"):new JsonArray();List<Mail> out=new ArrayList<>();a.forEach(v->{JsonObject m=v.getAsJsonObject();out.add(new Mail(str(m,"id"),str(m,"fromUid"),str(m,"fromName"),m.has("coins")?m.get("coins").getAsLong():0,m.has("message")?str(m,"message"):"",str(m,"status"),m.has("createdAt")?m.get("createdAt").getAsLong():0));});return Collections.unmodifiableList(out);}catch(Exception e){return List.of();}}
    private static Inventory parseInventory(String r){try{JsonObject o=JsonParser.parseString(r==null?"{}":r).getAsJsonObject();java.util.Set<String> owned=new java.util.LinkedHashSet<>();if(o.has("owned")&&o.get("owned").isJsonArray())o.getAsJsonArray("owned").forEach(v->owned.add(v.getAsString()));Map<String,String> equipped=new LinkedHashMap<>();if(o.has("equipped")&&o.get("equipped").isJsonObject())o.getAsJsonObject("equipped").entrySet().forEach(e->equipped.put(e.getKey(),e.getValue().getAsString()));return new Inventory(owned,equipped);}catch(Exception e){return Inventory.empty();}}
    private static RewardStatus parseRewardStatus(String r){try{JsonObject o=JsonParser.parseString(r==null?"{}":r).getAsJsonObject();JsonObject d=o.has("daily")?o.getAsJsonObject("daily"):new JsonObject(),a=o.has("activity")?o.getAsJsonObject("activity"):new JsonObject();return new RewardStatus(d.has("lastClaimDate")&&!d.get("lastClaimDate").isJsonNull()?d.get("lastClaimDate").getAsString():null,d.has("cycleCoins")?d.get("cycleCoins").getAsInt():0,d.has("maxCycleCoins")?d.get("maxCycleCoins").getAsInt():5,a.has("enabled")&&a.get("enabled").getAsBoolean(),a.has("usedToday")?a.get("usedToday").getAsInt():0,a.has("dailyCap")?a.get("dailyCap").getAsInt():5);}catch(Exception e){return RewardStatus.empty();}}
    private static String str(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    public record Wallet(boolean available,long balance){static Wallet unavailable(){return new Wallet(false,0);}}
    public record Item(String id,String name,String description,long priceCoins){}
    public record Catalog(String name,String symbol,String iconPath,List<Item> items){static Catalog empty(){return new Catalog("Elite Coin","EC","/assets/worldgate/elite/elite-coin.png",List.of());}}
    public record Mail(String id,String fromUid,String fromName,long coins,String message,String status,long createdAt){}
    public record RewardStatus(String lastClaimDate,int cycleCoins,int maxCycleCoins,boolean activityEnabled,int activityUsedToday,int activityDailyCap){static RewardStatus empty(){return new RewardStatus(null,0,5,false,0,5);}}
    public record Inventory(java.util.Set<String> owned,Map<String,String> equipped){static Inventory empty(){return new Inventory(java.util.Set.of(),Map.of());}public boolean owns(String id){return owned.contains(id);}public boolean equipped(String type,String id){return id.equals(equipped.get(type));}}
}