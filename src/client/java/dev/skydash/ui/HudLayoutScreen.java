package dev.skydash.ui;
import dev.skydash.*;
import dev.skydash.module.Module;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
public final class HudLayoutScreen extends Screen {
    private final Screen parent;
    private Module dragging;
    private double ox,oy;
    private final SkyDash mod=SkyDash.INSTANCE;
    public HudLayoutScreen(Screen parent) {
        super(Component.literal("SkyDash HUD layout"));
        this.parent=parent;
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done / save layout"),b->onClose()).bounds(width/2-85,height-25,170,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        g.fill(0,0,width,height,0x77101827);
        for(var m:mod.modules)if(m.enabled()&&m.config().pinned)m.render(g,Math.clamp(m.config().x,0,Math.max(0,width-210)),Math.clamp(m.config().y,0,Math.max(0,height-65)),210,3);
        g.centeredText(font,"Drag pinned panels to move",width/2,5,0xFFFFFFFF);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        if(super.mouseClicked(e,doubleClick))return true;
        if(e.button()!=0)return false;
        for(int i=mod.modules.size()-1; i>=0; i--) {
            var m=mod.modules.get(i);
            if(!m.enabled()||!m.config().pinned)continue;
            var c=m.config();
            c.x=Math.clamp(c.x,0,Math.max(0,width-210));
            c.y=Math.clamp(c.y,0,Math.max(0,height-65));
            if(e.x()>=c.x&&e.x()<=c.x+210&&e.y()>=c.y&&e.y()<=c.y+65) {
                dragging=m;
                ox=e.x()-c.x;
                oy=e.y()-c.y;
                return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(dragging==null)return super.mouseDragged(e,dx,dy);
        dragging.config().x=Math.clamp((int)(e.x()-ox),0,Math.max(0,width-210));
        dragging.config().y=Math.clamp((int)(e.y()-oy),0,Math.max(0,height-65));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(dragging!=null) {
            dragging=null;
            mod.save();
            return true;
        }
        return super.mouseReleased(e);
    }
    @Override public void onClose() {
        mod.save();
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() {
        return false;
    }
}
