package com.showzky.elevatormod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Powered Pulley and Floor Connector screens. The Elevator Controller has its own screen (ElevatorControllerScreen). */
public final class ElevatorControlScreen extends AbstractContainerScreen<ElevatorControlMenu> {
 private EditBox name;
 private boolean nameLoaded;
 private Button assembly;
 private ElevatorSpeedSlider speedSlider;
 public ElevatorControlScreen(ElevatorControlMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=216;imageHeight=menu.kind==1?146:218;}
 private void send(String action,CompoundTag data){PacketDistributor.sendToServer(new ElevatorControlPayload(menu.containerId,action,data));}
 @Override protected void init(){
  super.init();
  if(menu.kind==1){
   name=new EditBox(font,leftPos+12,topPos+50,192,20,Component.literal("Floor name"));name.setMaxLength(48);addRenderableWidget(name);
   addRenderableWidget(Button.builder(Component.literal("Save"),b->{CompoundTag t=new CompoundTag();t.putString("Name",name.getValue());send("name",t);}).bounds(leftPos+12,topPos+104,192,20).build());
  }else{
   speedSlider=addRenderableWidget(new ElevatorSpeedSlider(leftPos+12,topPos+PULLEY_SLIDER_Y,192,20,speed->{CompoundTag t=new CompoundTag();t.putDouble("Speed",speed);send("speed",t);}));
   assembly=addRenderableWidget(Button.builder(Component.literal("Connect"),b->send(menu.state.getBoolean("Active")?"park":"connect",new CompoundTag())).bounds(leftPos+12,topPos+192,192,20).build());
  }
  addRenderableWidget(Button.builder(Component.literal("x"),b->onClose()).bounds(leftPos+imageWidth-24,topPos+6,18,18).build());
 }
 @Override protected void containerTick(){
  if(name!=null&&!nameLoaded&&menu.state.contains("Name")){name.setValue(menu.state.getString("Name"));nameLoaded=true;}
  if(speedSlider!=null && menu.state.contains("Speed")) speedSlider.showServerSpeed(menu.state.getDouble("Speed"));
  if(assembly!=null)assembly.setMessage(Component.literal(menu.state.getBoolean("Active")?"Park":"Connect"));
 }
 @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
  int x=leftPos,y=topPos;
  var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID,"textures/gui/"+(menu.kind==0?"pulley":"connector")+".png");
  g.blit(texture,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
  if(menu.kind==1){text(g,"Floor name",12,38,0xff444444);text(g,"Height: Y "+menu.pos.getY(),12,80,0xff444444);text(g,menu.state.getBoolean("Connected")?"Connected":"Not connected",120,80,0xff267328);}
  else renderPulleyPanel(g,x,y);
 }
 private static final int PULLEY_SLIDER_Y=96;
 private static final int LABEL=0xff6b6b6b, TEXT=0xff3a3a3a, DIVIDER=0xff8b8b8b;
 /** Energy, speed and status sections of the Powered Pulley screen. */
 private void renderPulleyPanel(GuiGraphics g,int x,int y){
  int stored=Math.max(0,menu.state.getInt("Energy")),cap=Math.max(1,menu.state.getInt("Capacity"));
  double fraction=Math.min(1,stored/(double)cap);
  text(g,"ENERGY",12,32,LABEL);
  g.fill(x+12,y+43,x+204,y+55,0xff444444);
  g.fill(x+13,y+44,x+203,y+54,0xff262626);
  int barColor=fraction>0.25?0xff3fa34d:fraction>0.1?0xffd9a21b:0xffbe3434;
  g.fill(x+13,y+44,x+13+(int)Math.round(190*fraction),y+54,barColor);
  text(g,String.format(java.util.Locale.ROOT,"%,d / %,d FE",stored,cap),12,59,TEXT);
  rightText(g,Math.round(fraction*100)+"%",204,59,TEXT);
  g.fill(x+12,y+74,x+204,y+75,DIVIDER);
  text(g,"SPEED",12,83,LABEL);
  double speed=speedSlider!=null?speedSlider.speed():ElevatorEnergyUsage.DEFAULT_SPEED;
  text(g,"Uses "+ElevatorEnergyUsage.perTick(speed)+" FE/t while moving",12,121,TEXT);
  text(g,String.format(java.util.Locale.ROOT,"Current charge: ~%,d blocks",ElevatorEnergyUsage.range(stored,speed)),12,133,TEXT);
  g.fill(x+12,y+147,x+204,y+148,DIVIDER);
  text(g,"STATUS",12,155,LABEL);
  boolean active=menu.state.getBoolean("Active");
  String status=menu.state.getString("Status");
  boolean fault=status.startsWith("Waiting")||status.contains("blocked")||status.contains("Obstructed")||status.contains("unavailable")||status.contains("failed");
  boolean connected=menu.state.getBoolean("Connected");
  g.fill(x+12,y+168,x+18,y+174,active||connected?0xff3fa34d:0xff8b8b8b);
  text(g,active?"Cabin moving":connected?"Connected":"Not connected",22,167,active||connected?0xff267328:TEXT);
  rightText(g,status,204,167,fault?0xffa32a2a:TEXT);
 }
 private void rightText(GuiGraphics g,String s,int right,int y,int color){
  String fitted=font.plainSubstrByWidth(s,96);
  g.drawString(font,fitted,leftPos+right-font.width(fitted),topPos+y,color,false);
 }
 private void text(GuiGraphics g,String s,int x,int y,int color){g.drawString(font,font.plainSubstrByWidth(s,imageWidth-x-12),leftPos+x,topPos+y,color,false);}
 @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,12,12,0xff333333,false);}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
 @Override public boolean keyPressed(int key,int scan,int mods) {
  if(name!=null&&name.isFocused()&&key!=256)return name.keyPressed(key,scan,mods);
  return super.keyPressed(key,scan,mods);
 }
}
