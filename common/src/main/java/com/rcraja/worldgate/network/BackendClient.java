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

    public static String eliteProfile(FirebaseSession s,String uid){if(!ready(s))return null;return request(Constants.BACKEND_BASE_URL+"/v1/elite/profile",s.idToken(),"{"uid":""+escape(uid)+""}");}
    public static String publicEliteProfile(FirebaseSession s,String uid){if(!ready(s))return null;return request(Constants.BACKEND_BASE_URL+"/v1/elite/public-profiles",s.idToken(),"{"uids":[""+escape(uid)+""]}");}
    public static String coinWallet(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/wallet",s);}
    public static String coinCatalog(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/catalog",s);}
    public static String coinTransactions(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/coins/transactions",s);}
    public static String purchaseCoinPackage(FirebaseSession s,String packageId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/purchase-intent",s,"{"packageId":""+escape(packageId)+""}");}
    public static String purchaseItem(FirebaseSession s,String itemId,String idempotencyKey){return post(Constants.BACKEND_BASE_URL+"/v1/coins/shop/purchase",s,"{"itemId":""+escape(itemId)+"","idempotencyKey":""+escape(idempotencyKey)+""}");}
    private static boolean ready(FirebaseSession s){return s!=null&&s.isReady()&&s.idToken()!=null&&!s.idToken().isBlank();}
    private static String get(String url,FirebaseSession s){if(!ready(s))return null;try{HttpRequest r=HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+s.idToken()).GET().build();HttpResponse<String> h=CLIENT.send(r,HttpResponse.BodyHandlers.ofString());if(h.statusCode()<200||h.statusCode()>=300){WorldGateMod.LOGGER.warn("WorldGate backend returned HTTP {} for {}",h.statusCode(),url);return null;}return h.body();}catch(Exception e){WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}",url,e);return null;}}
    private static String post(String url,FirebaseSession s,String body){if(!ready(s))return null;return request(url,s.idToken(),body);}
    private static String request(String url,String token,String body){try{HttpRequest r=HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> h=CLIENT.send(r,HttpResponse.BodyHandlers.ofString());if(h.statusCode()<200||h.statusCode()>=300){WorldGateMod.LOGGER.warn("WorldGate backend returned HTTP {} for {}",h.statusCode(),url);return null;}return h.body();}catch(Exception e){WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}",url,e);return null;}}
    private static String escape(String v){if(v==null)return "";return v.replace("\\","\\\\").replace(""","\\"");}
}