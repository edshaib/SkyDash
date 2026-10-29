package dev.skydash.ui;
import dev.skydash.*;
import dev.skydash.module.Module;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class DashboardScreen extends Screen {
    private final Screen parent;
    private int page,selected,offset;
    private final SkyDash mod=SkyDash.INSTANCE;
    public DashboardScreen(Screen parent) {
        super(Component.literal("SkyDash"));
        this.parent=parent;
    }
    @Override public void added() {
        if (mod.context.session.active()) mod.api.requestProfile(mod.config);
    }
    private void button(String label,int x,int y,int w,Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        int rows=Math.max(1,(height-108)/24);
        int start=page*rows;
        if(start>=mod.modules.size()) {
            page=0;
            start=0;
        }
        for(int i=start; i<Math.min(start+rows,mod.modules.size()); i++) {
            final int index=i;
            Module m=mod.modules.get(i);
            int y=45+(i-start)*24;
            button(m.name(),10,y,Math.min(160,width/3),()-> {
                selected=index; offset=0;
            });
            int x=20+Math.min(160,width/3);
            button(m.enabled()?"On":"Off",x,y,36,()-> {
                m.config().enabled=!m.enabled(); mod.save(); rebuildWidgets();
            });
            button(m.config().pinned?"Pinned":"Pin",x+40,y,46,()-> {
                m.config().pinned=!m.config().pinned; mod.save(); rebuildWidgets();
            });
        }
        button("Modules <",10,height-55,84,()-> {
            page=Math.max(0,page-1); rebuildWidgets();
        });
        button(">",98,height-55,30,()-> {
            page=(page+1)*rows<mod.modules.size()?page+1:0; rebuildWidgets();
        });
        button("Refresh",10,height-28,70,()-> {
            mod.api.manualRefresh(mod.config);
        });
        button("Settings",84,height-28,70,()->minecraft.setScreen(new SettingsScreen(this)));
        button("HUD layout",158,height-28,82,()->minecraft.setScreen(new HudLayoutScreen(this)));
        button("Done",width-65,height-28,55,this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        g.fill(0,0,width,height,0xF00B111C);
        g.text(font,"SkyDash / your SkyBlock",12,12,0xFF55DCC2);
        g.text(font,font.plainSubstrByWidth(mod.api.status(),width-24),12,28,0xFF9FAEBE);
        int x=116+Math.min(160,width/3);
        if(width-x>70) {
            var m=mod.modules.get(Math.clamp(selected,0,mod.modules.size()-1));
            g.text(font,m.name(),x,48,0xFFFFFFFF);
            int count=Math.max(1,(height-120)/12);
            var lines=m.lines();
            for(int i=0; i<count&&offset+i<lines.size(); i++)g.text(font,font.plainSubstrByWidth(lines.get(offset+i),width-x-10),x,66+i*12,0xFFD9E5F2);
        }
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        offset=Math.clamp(offset-(int)dy,0,Math.max(0,mod.modules.get(selected).lines().size()-1));
        return true;
    }
    @Override public void onClose() {
        mod.save();
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() {
        return false;
    }
}
