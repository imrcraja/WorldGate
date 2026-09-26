package com.rcraja.worldgate.client.gui;
import com.rcraja.worldgate.network.VoiceManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.Desktop;
import java.net.URI;
public final class VoicePermissionScreen extends Screen{
 private final Screen parent; private final VoiceManager voice; private String status="WorldGate Voice needs microphone access.";
 public VoicePermissionScreen(Screen parent,VoiceManager voice){super(Component.literal("WorldGate Voice"));this.parent=parent;this.voice=voice;}
 protected void init(){int w=Math.min(440,width-40),x=(width-w)/2,y=height/2-35;
  addRenderableWidget(Button.builder(Component.literal("Allow / Test Microphone"),b->{if(voice.microphoneAvailable()){status="Microphone is available.";onClose();}else status="Microphone access is blocked. Allow it for Minecraft or your launcher.";}).bounds(x,y,w,28).build());
  addRenderableWidget(Button.builder(Component.literal("Open Privacy Settings"),b->openPrivacy()).bounds(x,y+36,w,28).build());
  addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x,y+72,w,28).build());
 }
 private void openPrivacy(){try{String os=System.getProperty("os.name","").toLowerCase();String target=os.contains("win")?"ms-settings:privacy-microphone":os.contains("mac")?"x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone":"https://support.google.com/android/answer/9431959";if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(target));status="Privacy settings opened."; }catch(Exception e){status="Open your system microphone privacy settings and allow Minecraft/launcher.";}}
 public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
 public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);g.centeredText(font,"WorldGate Voice",width/2,height/2-95,0xFFFFFFFF);g.centeredText(font,status,width/2,height/2-72,0xFFB9C8D8);}
}