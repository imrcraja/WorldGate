package com.rcraja.worldgate.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EliteCoinManager {
    private static volatile Wallet wallet=new Wallet(0,false);
    private static volatile Catalog catalog=Catalog.empty();
    private EliteCoinManager(){}
    public static void refresh(){FirebaseSession s=WorldGateModClient.SESSION;WorldGateModClient.EXECUTOR.submit(()->{wallet=parseWallet(BackendClient.coinWallet(s));catalog=parseCatalog(BackendClient.coinCatalog(s));});}
    public static Wallet wallet(){return wallet;}
    public static Catalog catalog(){return catalog;}
    public static String purchaseItem(String id){String key=UUID.randomUUID().toString().replace("-","");return BackendClient.purchaseItem(WorldGateModClient.SESSION,id,key);}
    public static String requestPackage(String id){return BackendClient.purchaseCoinPackage(WorldGateModClient.SESSION,id);}
    private static Wallet parseWallet(String raw){if(raw==null)return new Wallet(0,false);try{JsonObject o=JsonParser.parseString(raw).getAsJsonObject();return new Wallet(Math.max(0,o.get("balance").getAsLong()),true);}catch(Exception e){return new Wallet(0,false);}}
    private static Catalog parseCatalog(String raw){if(raw==null)return Catalog.empty();try{JsonObject o=JsonParser.parseString(raw).getAsJsonObject(),coin=o.getAsJsonObject("coin");List<Item> items=new ArrayList<>();JsonArray a=o.getAsJsonArray("items");if(a!=null)for(var e:a){JsonObject i=e.getAsJsonObject();items.add(new Item(i.get("id").getAsString(),i.get("name").getAsString(),i.has("description")?i.get("description").getAsString():"",i.get("priceCoins").getAsLong()));}return new Catalog(coin.get("name").getAsString(),coin.get("symbol").getAsString(),coin.get("currency").getAsString(),coin.has("priceMinorUnitsPerCoin")?coin.get("priceMinorUnitsPerCoin").getAsLong():0,items);}catch(Exception e){return Catalog.empty();}}
    public record Wallet(long balance,boolean available){}
    public record Item(String id,String name,String description,long priceCoins){}
    public record Catalog(String name,String symbol,String currency,long priceMinorUnitsPerCoin,List<Item> items){static Catalog empty(){return new Catalog("Elite Coin","EC","INR",0,List.of());}}
}