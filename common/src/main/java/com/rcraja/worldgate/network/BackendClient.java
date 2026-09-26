package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendClient {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 400L;
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BackendClient(){}

    public static String startOnlineSession(FirebaseSession s,String sessionId,String deviceName){return post(Constants.BACKEND_BASE_URL+"/v1/session/start",s,"{\"sessionId\":\""+escape(sessionId)+"\",\"deviceName\":\""+escape(deviceName)+"\"}");}
    public static String heartbeatOnlineSession(FirebaseSession s,String sessionId){return post(Constants.BACKEND_BASE_URL+"/v1/session/heartbeat",s,"{\"sessionId\":\""+escape(sessionId)+"\"}");}
    public static String endOnlineSession(FirebaseSession s,String sessionId){return post(Constants.BACKEND_BASE_URL+"/v1/session/end",s,"{\"sessionId\":\""+escape(sessionId)+"\"}");}
    public static String listOnlineSessions(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/session/list",s);}
    public static String revokeOnlineSession(FirebaseSession s,String sessionId){return post(Constants.BACKEND_BASE_URL+"/v1/session/revoke",s,"{\"sessionId\":\""+escape(sessionId)+"\"}");}
    public static String revokeOtherOnlineSessions(FirebaseSession s,String sessionId){return post(Constants.BACKEND_BASE_URL+"/v1/session/revoke-all-other",s,"{\"sessionId\":\""+escape(sessionId)+"\"}");}

    public static String discordAuthorize(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/auth/discord",s);}
    public static String discordResult(String ticket){return getPublic(Constants.BACKEND_BASE_URL+"/auth/discord/result?ticket="+escape(ticket));}
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
    public static String paymentPackages(){return getPublic(Constants.BACKEND_BASE_URL+"/v1/payments/packages");}
    public static String paymentStatus(String eventId){return getPublic(Constants.BACKEND_BASE_URL+"/v1/payments/status/"+escape(eventId));}
    public static String supportTickets(FirebaseSession s){return get(Constants.BACKEND_BASE_URL+"/v1/support/tickets",s);}
    public static String createSupportTicket(FirebaseSession s,String subject,String category,String body){return post(Constants.BACKEND_BASE_URL+"/v1/support/tickets",s,"{\"subject\":\""+escape(subject)+"\",\"category\":\""+escape(category)+"\",\"body\":\""+escape(body)+"\"}");}
    public static String supportTicketMessage(FirebaseSession s,String ticketId,String body){return post(Constants.BACKEND_BASE_URL+"/v1/support/tickets/message",s,"{\"ticketId\":\""+escape(ticketId)+"\",\"body\":\""+escape(body)+"\"}");}
    public static String closeSupportTicket(FirebaseSession s,String ticketId){return post(Constants.BACKEND_BASE_URL+"/v1/support/tickets/close",s,"{\"ticketId\":\""+escape(ticketId)+"\"}");}
    public static String giveaways(){return getPublic(Constants.BACKEND_BASE_URL+"/v1/giveaways");}
    public static String enterGiveaway(FirebaseSession s,String slug){return post(Constants.BACKEND_BASE_URL+"/v1/giveaways/enter",s,"{\"slug\":\""+escape(slug)+"\"}");}
    public static String purchaseItem(FirebaseSession s,String itemId,String idempotencyKey){return post(Constants.BACKEND_BASE_URL+"/v1/coins/shop/purchase",s,"{\"itemId\":\""+escape(itemId)+"\",\"idempotencyKey\":\""+escape(idempotencyKey)+"\"}");}
    public static String equipItem(FirebaseSession s,String itemId){return post(Constants.BACKEND_BASE_URL+"/v1/coins/shop/equip",s,"{\"itemId\":\""+escape(itemId)+"\"}");}

    private static boolean ready(FirebaseSession s){return s!=null&&s.isReady()&&s.idToken()!=null&&!s.idToken().isBlank();}

    private static String get(String url,FirebaseSession s){
        if(!ready(s))return null;
        return request(url,s.idToken(),null,false,15);
    }

    private static String getPublic(String url){
        return request(url,null,null,false,10);
    }

    private static String post(String url,FirebaseSession s,String body){
        if(!ready(s))return null;
        return request(url,s.idToken(),body,true,15);
    }

    private static String request(String url,String token,String body,boolean post,int timeoutSeconds){
        for(int attempt=1;attempt<=MAX_ATTEMPTS;attempt++){
            try{
                HttpRequest.Builder builder=HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSeconds));
                if(token!=null) builder.header("Authorization","Bearer "+token);
                if(post){
                    builder.header("Content-Type","application/json");
                    builder.POST(HttpRequest.BodyPublishers.ofString(body==null?"":body));
                }else{
                    builder.GET();
                }

                HttpResponse<String> response=CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString());
                int status=response.statusCode();
                if(status>=200&&status<300)return response.body();

                boolean retryable=status==408||status==425||status==429||status>=500;
                if(!retryable||attempt==MAX_ATTEMPTS){
                    WorldGateMod.LOGGER.warn("WorldGate backend returned HTTP {} for {}",status,url);
                    return null;
                }
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                WorldGateMod.LOGGER.warn("WorldGate backend request interrupted: {}",url);
                return null;
            }catch(Exception e){
                if(attempt==MAX_ATTEMPTS){
                    WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}",url,e);
                    return null;
                }
            }

            try{
                Thread.sleep(RETRY_DELAY_MS*attempt);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String escape(String v){
        if(v==null)return "";
        return v.replace("\\","\\\\").replace("\"","\\\"");
    }
}
