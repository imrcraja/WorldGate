package com.rcraja.worldgate.client;

import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.network.BackendClient;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WorldGateOnlineSession {
    private static final String KEY="onlineSessionId";
    private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"WorldGate-Online-Session");t.setDaemon(true);return t;});
    private static volatile String sessionId;
    private static volatile boolean running;
    private static volatile boolean registered;

    private WorldGateOnlineSession(){}

    public static synchronized void start(){
        if(running||!WorldGateModClient.SESSION.isReady())return;
        sessionId=LocalWorldGateData.get(KEY);
        if(sessionId==null||sessionId.isBlank()){
            sessionId=UUID.randomUUID().toString().replace("-","");
            LocalWorldGateData.set(KEY,sessionId);
        }
        running=true;
        registered=false;
        EXECUTOR.execute(WorldGateOnlineSession::refresh);
        EXECUTOR.scheduleAtFixedRate(WorldGateOnlineSession::refresh,10,10,TimeUnit.SECONDS);
    }

    private static String currentVersionName(){
        Object version=net.minecraft.SharedConstants.getCurrentVersion();
        try{
            return String.valueOf(version.getClass().getMethod("getName").invoke(version));
        }catch(ReflectiveOperationException ignored){
            try{
                return String.valueOf(version.getClass().getMethod("name").invoke(version));
            }catch(ReflectiveOperationException ignoredAgain){
                return String.valueOf(version);
            }
        }
    }

    private static void refresh(){
        if(!running||!WorldGateModClient.SESSION.isReady())return;
        String response;
        String device="Minecraft "+currentVersionName();
        if(!registered){
            response=BackendClient.startOnlineSession(WorldGateModClient.SESSION,sessionId,device);
            if(response!=null)registered=true;
        }else{
            response=BackendClient.heartbeatOnlineSession(WorldGateModClient.SESSION,sessionId);
            if(response==null){
                registered=false;
                response=BackendClient.startOnlineSession(WorldGateModClient.SESSION,sessionId,device);
                if(response!=null)registered=true;
            }
        }
        if(response==null)WorldGateMod.LOGGER.debug("WorldGate online-session heartbeat failed");
    }

    public static synchronized void stop(){
        running=false;
        registered=false;
        String id=sessionId;
        if(id!=null&&WorldGateModClient.SESSION.isReady())
            WorldGateModClient.EXECUTOR.submit(()->BackendClient.endOnlineSession(WorldGateModClient.SESSION,id));
    }

    public static String id(){return sessionId;}
    public static boolean isRunning(){return running;}
}