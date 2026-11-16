package com.showzky.elevatormod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ElevatorControlScreen extends AbstractContainerScreen<ElevatorControlMenu> {
 private EditBox name;
 private boolean nameLoaded;
 private static final int VISIBLE_FLOORS = 4;
 private static final int LIST_TOP = 98;
 private static final int ROW_HEIGHT = 23;
 private static final int TRACK_HEIGHT = VISIBLE_FLOORS * ROW_HEIGHT - 2;
 private static final net.minecraft.resources.ResourceLocation SCROLLER = net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/scroller");
 private static final net.minecraft.resources.ResourceLocation SCROLLER_BACKGROUND = net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/scroller_background");
 private int offset;
 private boolean draggingScrollbar;
 private double scrollbarGrabOffset;
 private final Button[] floors=new Button[VISIBLE_FLOORS];
 private Button assembly;
 private SpeedSlider speedSlider;
 private double lastServerSpeed=Double.NaN;
 public ElevatorControlScreen(ElevatorControlMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=216;imageHeight=menu.kind==1?146:218;}
 private void send(String action,CompoundTag data){PacketDistributor.sendToServer(new ElevatorControlPayload(menu.containerId,action,data));}
 @Override protected void init(){
  super.init();
  draggingScrollbar=false;
  if(menu.kind==1){
   name=new EditBox(font,leftPos+12,topPos+50,192,20,Component.literal("Floor name"));name.setMaxLength(48);addRenderableWidget(name);
   addRenderableWidget(Button.builder(Component.literal("Save"),b->{CompoundTag t=new CompoundTag();t.putString("Name",name.getValue());send("name",t);}).bounds(leftPos+12,topPos+104,192,20).build());
  }else if(menu.kind==0){
   lastServerSpeed=Double.NaN;
   speedSlider=addRenderableWidget(new SpeedSlider(leftPos+12,topPos+PULLEY_SLIDER_Y,192,20));
   assembly=addRenderableWidget(Button.builder(Component.literal("Connect"),b->send(menu.state.getBoolean("Active")?"park":"connect",new CompoundTag())).bounds(leftPos+12,topPos+192,192,20).build());
  }else{
   for (int i=0; i<VISIBLE_FLOORS; i++) {
    final int row=i;
    floors[i]=addRenderableWidget(Button.builder(Component.empty(),button -> {
     ListTag list=menu.state.getList("Floors",10);
     int index=list.size()-1-(offset+row);
     if (index>=0 && index<list.size()) {
      CompoundTag data=new CompoundTag();data.putInt("Y",list.getCompound(index).getInt("Y"));send("travel",data);
     }
    }).bounds(leftPos+12,topPos+LIST_TOP+i*ROW_HEIGHT,192,21).build());
   }
  }
  int closeSize=menu.kind==2?13:18;
  addRenderableWidget(Button.builder(Component.literal("x"),b->onClose()).bounds(leftPos+imageWidth-closeSize-6,topPos+(menu.kind==2?7:6),closeSize,closeSize).build());
  updateFloorButtons();
 }
 @Override protected void containerTick(){
  if(name!=null&&!nameLoaded&&menu.state.contains("Name")){name.setValue(menu.state.getString("Name"));nameLoaded=true;}
  if(speedSlider!=null && menu.state.contains("Speed")) {
   // Show the saved speed when it first arrives or when someone else changes it.
   double server=menu.state.getDouble("Speed");
   if(server!=lastServerSpeed){lastServerSpeed=server;speedSlider.showServerSpeed(server);}
  }
  if(assembly!=null)assembly.setMessage(Component.literal(menu.state.getBoolean("Active")?"Park":"Connect"));
  updateFloorButtons();
 }
 @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
  int x=leftPos,y=topPos;
  var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID,"textures/gui/"+(menu.kind==0?"pulley":menu.kind==1?"connector":"controller")+".png");
  g.blit(texture,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
  if(menu.kind==1){text(g,"Floor name",12,38,0xff444444);text(g,"Height: Y "+menu.pos.getY(),12,80,0xff444444);text(g,menu.state.getBoolean("Connected")?"Connected":"Not connected",120,80,0xff267328);}
  else if(menu.kind==0){
   renderPulleyPanel(g,x,y);
  }else{
   g.fill(x+12,y+39,x+204,y+89,0xff2e3831);text(g,"CURRENT FLOOR",19,46,0xffa8d39d);String current=menu.state.contains("Y")?"Between floors":"No cabin detected";
   for(Tag tag:menu.state.getList("Floors",10)){CompoundTag f=(CompoundTag)tag;if(Math.abs(f.getInt("Y")-menu.state.getDouble("Y"))<0.001)current=f.getString("Name");}
   text(g,current,19,60,0xffa8d39d);
   String status=menu.state.getString("Status");
   if (status.equals("Going up") || status.equals("Going down")) {
    for (Tag tag:menu.state.getList("Floors",10)) {
     CompoundTag floor=(CompoundTag)tag;
     if (Math.abs(menu.state.getDouble("Target")-floor.getInt("Y"))<0.001) { status += " -> "+floor.getString("Name"); break; }
    }
   }
   text(g,status,19,75,0xffa8d39d);
   int count=menu.state.getList("Floors",10).size();
   if (count==0) text(g,"No floors found",12,LIST_TOP+6,0xff444444);
   if (count>VISIBLE_FLOORS) {
    int trackX=x+198,trackY=y+LIST_TOP,thumb=thumbHeight(count),thumbY=thumbTop(count);
    g.blitSprite(SCROLLER_BACKGROUND,trackX,trackY,6,TRACK_HEIGHT);
    g.blitSprite(SCROLLER,trackX,thumbY,6,thumb);
   }
   if (menu.state.contains("Energy") && menu.state.contains("Capacity")) {
    int footerY=LIST_TOP+Math.max(1,Math.min(count,VISIBLE_FLOORS))*ROW_HEIGHT+3;
    int energy=Math.max(0,menu.state.getInt("Energy")),capacity=Math.max(0,menu.state.getInt("Capacity"));
    g.fill(x+12,y+footerY,x+204,y+footerY+12,0xff2e3831);
    int width=capacity==0?0:(int)Math.min(184L,184L*energy/capacity);
    g.fill(x+16,y+footerY+4,x+16+width,y+footerY+8,0xff78a96e);
   }
  }
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
 /** Drags continuously, snaps to 0.5 m/s steps and only sends a new speed when the step changes. */
 private final class SpeedSlider extends AbstractSliderButton {
  private static final double MIN=ElevatorEnergyUsage.MIN_SPEED, MAX=ElevatorEnergyUsage.MAX_SPEED;
  private double sentSpeed=Double.NaN;
  private boolean dragging;
  SpeedSlider(int x,int y,int width,int height){super(x,y,width,height,Component.empty(),fraction(ElevatorEnergyUsage.DEFAULT_SPEED));updateMessage();}
  private static double fraction(double speed){return (speed-MIN)/(MAX-MIN);}
  double speed(){return ElevatorEnergyUsage.sanitizeSpeed(MIN+value*(MAX-MIN));}
  @Override protected void updateMessage(){setMessage(Component.literal(String.format(java.util.Locale.ROOT,"Speed: %.1f m/s",ElevatorEnergyUsage.metresPerSecond(speed()))));}
  @Override protected void applyValue(){
   double speed=speed();
   if(speed==sentSpeed)return;
   sentSpeed=speed;CompoundTag t=new CompoundTag();t.putDouble("Speed",speed);send("speed",t);
  }
  @Override public void onClick(double mouseX,double mouseY){dragging=true;super.onClick(mouseX,mouseY);}
  @Override public void onRelease(double mouseX,double mouseY){dragging=false;value=fraction(speed());updateMessage();super.onRelease(mouseX,mouseY);}
  void showServerSpeed(double speed){
   if(dragging)return;
   sentSpeed=speed;value=Math.clamp(fraction(speed),0,1);updateMessage();
  }
 }
 private void text(GuiGraphics g,String s,int x,int y,int color){g.drawString(font,font.plainSubstrByWidth(s,imageWidth-x-12),leftPos+x,topPos+y,color,false);}
 @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,12,12,0xff333333,false);}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
 private int maxOffset() { return Math.max(0,menu.state.getList("Floors",10).size()-VISIBLE_FLOORS); }
 private int thumbHeight(int count) { return Math.max(12,TRACK_HEIGHT*VISIBLE_FLOORS/count); }
 private int thumbTop(int count) { return topPos+LIST_TOP+(int)Math.round((TRACK_HEIGHT-thumbHeight(count))*(double)offset/Math.max(1,maxOffset())); }
 private void updateFloorButtons() {
  if(menu.kind!=2)return;
  ListTag list=menu.state.getList("Floors",10);
  offset=Math.max(0,Math.min(offset,maxOffset()));
  for(int row=0;row<VISIBLE_FLOORS;row++) {
   Button button=floors[row];if(button==null)continue;
   int index=list.size()-1-(offset+row);
   button.visible=index>=0 && index<list.size();
   button.setWidth(list.size()>VISIBLE_FLOORS?181:192);
   if(!button.visible)continue;
   CompoundTag floor=list.getCompound(index);
   boolean current=menu.state.contains("Y") && Math.abs(menu.state.getDouble("Y")-floor.getInt("Y"))<0.001;
   boolean travelling=menu.state.getString("Status").equals("Going up") || menu.state.getString("Status").equals("Going down");
   boolean destination=travelling && Math.abs(menu.state.getDouble("Target")-floor.getInt("Y"))<0.001;
   button.active=menu.state.getBoolean("CanTravel") && !current;
   button.setMessage(Component.literal((current?"> ":destination?"* ":"")+floor.getString("Name")));
  }
 }
 private void setOffsetFromMouse(double mouseY) {
  int count=menu.state.getList("Floors",10).size();
  if(count<=VISIBLE_FLOORS)return;
  int travel=TRACK_HEIGHT-thumbHeight(count);
  double fraction=(mouseY-(topPos+LIST_TOP)-scrollbarGrabOffset)/travel;
  offset=(int)Math.round(Math.max(0,Math.min(1,fraction))*maxOffset());
  updateFloorButtons();
 }
 @Override public boolean mouseClicked(double x,double y,int button) {
  if(menu.kind==2 && button==0 && maxOffset()>0 && x>=leftPos+198 && x<leftPos+204 && y>=topPos+LIST_TOP && y<topPos+LIST_TOP+TRACK_HEIGHT) {
   int thumbY=thumbTop(menu.state.getList("Floors",10).size());
   int thumb=thumbHeight(menu.state.getList("Floors",10).size());
   scrollbarGrabOffset=y>=thumbY && y<thumbY+thumb ? y-thumbY : thumb/2.0;
   draggingScrollbar=true;setOffsetFromMouse(y);return true;
  }
  return super.mouseClicked(x,y,button);
 }
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
  if(menu.kind==2 && button==0 && draggingScrollbar){setOffsetFromMouse(y);return true;}
  return super.mouseDragged(x,y,button,dx,dy);
 }
 @Override public boolean mouseReleased(double x,double y,int button) {
  if(button==0 && draggingScrollbar){draggingScrollbar=false;return true;}
  return super.mouseReleased(x,y,button);
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v) {
  if(menu.kind==2 && maxOffset()>0 && v!=0){offset=Math.max(0,Math.min(maxOffset(),offset-(int)Math.signum(v)));updateFloorButtons();return true;}
  return super.mouseScrolled(x,y,h,v);
 }
 @Override public boolean keyPressed(int key,int scan,int mods) {
  if(name!=null&&name.isFocused()&&key!=256)return name.keyPressed(key,scan,mods);
  if(menu.kind==2 && maxOffset()>0 && (key==org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key==org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) {
   offset=Math.max(0,Math.min(maxOffset(),offset+(key==org.lwjgl.glfw.GLFW.GLFW_KEY_UP?-1:1)));updateFloorButtons();return true;
  }
  return super.keyPressed(key,scan,mods);
 }
}
