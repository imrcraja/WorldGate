package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendClient {
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private BackendClient(){}
    public static String eliteProfile(FirebaseSession s,String uid){return post(Constants.BACKEND_BASE_URL+"/v1/elite/profile",s,"{\"uid\":\""+escape(uid)+"\"}");}
    public static String publicEliteProfile(FirebaseSession s,String uid){return post(Constants.BACKEND_BASE_URL+"/v1/elite/public-profiles",s,"{\"uids\":[\""+escape(uid)+"\"]}");}
    public static String coinWallet(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/wallet",s);}
    public static String coinCatalog(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/catalog",s);}
    public static String coinTransactions(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/transactions",s);}
    public static String coinInventory(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/inventory",s);}
    public static String coinMailbox(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/mailbox",s);}
    public static String coinRewardStatus(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/rewards/status",s);}
    public static String claimDaily(FirebaseSession s){return post(Constants.BACKEND_BASE_URL+"/v1/coins/rewards/daily-claim",s,"{}");}
    public static String startActivityReward(FirebaseSession s){return post(Constants.BACKEND_BASE_URL+"/v1/coins/rewards/activity/session",s,"{}");}
    public static String completeActivityReward(FirebaseSession s,String sessionId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/rewards/activity/complete",s,"{\"sessionId\":\""+escape(sessionId)+"\"}");}
    public static String giftCoins(FirebaseSession s,String toUid,long coins,String message){return post(Constants.BACKEND_BASE_URL+"/v1/coins/gift",s,"{\"toUid\":\""+escape(toUid)+"\",\"coins\":"+coins+",\"message\":\""+escape(message)+"\"}");}
    public static String markMailboxRead(FirebaseSession s,String messageId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/mailbox/read",s,"{\"messageId\":\""+escape(messageId)+"\"}");}
    public static String purchaseCoinPackage(FirebaseSession s,String packageId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/purchase-intent",s,"{\"packageId\":\""+escape(packageId)+"\"}");}
    public static String purchaseItem(FirebaseSession s,String itemId,String idempotencyKey){return post(Constants.BACKEND_BASE_URL+"/v1/coins/shop/purchase",s,"{\"itemId\":\""+escape(itemId)+"\",\"idempotencyKey\":\""+escape(idempotencyKey)+"\"}");}
    public static String equipItem(FirebaseSession s,String itemId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/shop/equip",s,"{\"itemId\":\""+escape(itemId)+"\"}");}
    private static boolean ready(FirebaseSession s){return s!=null&&s.isReady()&&s.idToken()!=null&&!s.idToken().isBlank();}
    private static String get(String url,FirebaseSession s){if(!ready(s))return null;try{HttpRequest r=HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+s.idToken()).GET().build();HttpResponse<String> h=CLIENT.send(r,HttpResponse.BodyHandlers.ofString());if(h.statusCode()<200||h.statusCode()>=300){WorldGateMod.LOGGER.warn("WorldGate backend returned HTTP {} for {}",h.statusCode(),url);return null;}return h.body();}catch(Exception e){WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}",url,e);return null;}}
    private static String post(String url,FirebaseSession s,String body){if(!ready(s))return null;return request(url,s.idToken(),body);}
    private static String request(String url,String token,String body){try{HttpRequest r=HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> h=CLIENT.send(r,HttpResponse.BodyHandlers.ofString());if(h.statusCode()<200||h.statusCode()>=300){WorldGateMod.LOGGER.warn("WorldGate backend returned HTTP {} for {}",h.statusCode(),url);return null;}return h.body();}catch(Exception e){WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}",url,e);return null;}}
    private static String escape(String v){if(v==null)return "";return v.replace("\\","\\\\").replace("\"","\\\"");}
}