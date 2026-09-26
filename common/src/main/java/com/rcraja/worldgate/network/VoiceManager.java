package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.client.WorldGateModClient;
import javax.sound.sampled.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

public final class VoiceManager {
    private final FirebaseSession session;
    private volatile WebSocket socket;
    private volatile TargetDataLine microphone;
    private volatile SourceDataLine speaker;
    private volatile boolean running;
    private volatile boolean microphoneDenied;
    private ExecutorService io;
    public VoiceManager(FirebaseSession session){this.session=session;}
    public boolean isRunning(){return running;}
    public boolean microphoneAvailable(){
        try{
            AudioFormat f=new AudioFormat(16000f,16,1,true,false);
            TargetDataLine line=AudioSystem.getTargetDataLine(f);
            line.close();microphoneDenied=false;return true;
        }catch(Exception e){microphoneDenied=true;return false;}
    }
    public boolean start(String room,String role){
        if(running||room==null||room.isBlank())return false;
        if(!microphoneAvailable())return false;
        try{
            AudioFormat f=new AudioFormat(16000f,16,1,true,false);
            microphone=AudioSystem.getTargetDataLine(f);microphone.open(f);
            speaker=AudioSystem.getSourceDataLine(f);speaker.open(f);speaker.start();microphone.start();
            io=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"WorldGate-Voice");t.setDaemon(true);return t;});
            running=true;
            HttpClient client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            VoiceListener listener=new VoiceListener();
            socket=client.newWebSocketBuilder().buildAsync(URI.create(Constants.RELAY_WS_URL),listener).get(5,TimeUnit.SECONDS);
            String uid=WorldGateModClient.SESSION.uid();
            String hs="{"protocol":2,"channel":"voice","role":""+escape(role)+"","room":""+escape(room)+"","uid":""+escape(uid==null?"":uid)+""}";
            socket.sendText(hs,true);
            io.submit(()->capture(f));
            return true;
        }catch(Exception e){WorldGateMod.LOGGER.warn("WorldGate voice start failed",e);stop();return false;}
    }
    private void capture(AudioFormat f){
        byte[] buf=new byte[640];
        try{while(running&&microphone!=null){int n=microphone.read(buf,0,buf.length);if(n>0&&socket!=null)socket.sendBinary(ByteBuffer.wrap(java.util.Arrays.copyOf(buf,n)),true);}}catch(Exception e){if(running)WorldGateMod.LOGGER.debug("Voice capture stopped",e);}
    }
    public void stop(){
        running=false;
        try{if(socket!=null)socket.sendClose(WebSocket.NORMAL_CLOSURE,"voice stop");}catch(Exception ignored){}
        try{if(microphone!=null)microphone.stop();}catch(Exception ignored){}
        try{if(microphone!=null)microphone.close();}catch(Exception ignored){}
        try{if(speaker!=null)speaker.drain();}catch(Exception ignored){}
        try{if(speaker!=null)speaker.close();}catch(Exception ignored){}
        if(io!=null){io.shutdownNow();io=null;}socket=null;microphone=null;speaker=null;
    }
    private final class VoiceListener implements WebSocket.Listener{
        public CompletionStage<?> onBinary(WebSocket ws,ByteBuffer data,boolean last){
            try{if(speaker!=null){byte[] b=new byte[data.remaining()];data.get(b);speaker.write(b,0,b.length);}}catch(Exception ignored){}
            ws.request(1);return null;
        }
        public CompletionStage<?> onText(WebSocket ws,String data,boolean last){ws.request(1);return null;}
        public void onOpen(WebSocket ws){ws.request(1);}
        private CompletionStage<?> done(){return null;}
    }
    private static String escape(String s){return s.replace("\\","\\\\").replace(""","\\"");}
}