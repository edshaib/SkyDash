package dev.skydash.ui;
import dev.skydash.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SkyDash mod=SkyDash.INSTANCE;
    private int page;
    private String message="";
    public SettingsScreen(Screen p) {
        super(Component.literal("SkyDash settings"));
        parent=p;
    }
    private void field(String label,String value,int y,Consumer<String> setter) {
        var box=new EditBox(font,width/2,y,Math.max(85,width/2-20),20,Component.literal(label));
        box.setMaxLength(512);
        box.setValue(value);
        box.setResponder(v-> {
            try {
                setter.accept(v); message="";
            } catch(RuntimeException e) {
                message="Invalid value; previous setting kept";
            }
        });
        addRenderableWidget(box);
        labels.add(label);
    }
    private final List<String> labels=new ArrayList<>();
    @Override protected void init() {
        labels.clear();
        var c=mod.config;
        int y=55;
        if(page==0) {
            field("Profile refresh (120-300s)",""+c.profileTtl(),y,v->c.profileRefreshSeconds=Math.clamp(Integer.parseInt(v),120,300));
            field("Public fallback (30-3600s)",""+c.publicTtl(),y+26,v->c.publicRefreshSeconds=Math.clamp(Integer.parseInt(v),30,3600));
            field("HTTPS backend base URL",c.proxyUrl,y+52,v-> {
                if(!v.isEmpty()) {
                    var u=java.net.URI.create(v); if(!"https".equals(u.getScheme())||u.getHost()==null||u.getQuery()!=null||u.getUserInfo()!=null)throw new IllegalArgumentException();
                }
                c.proxyUrl=v;
            });
            field("Own profile UUID (optional)",c.profileId,y+78,v-> {
                if(!v.isEmpty()&&!v.matches("[a-fA-F0-9-]{32,36}"))throw new IllegalArgumentException(); c.profileId=v;
            });
        }
        if(page==1) {
            field("Goal skill (SKILL_FARMING)",c.goalSkill,y,v->c.goalSkill=v.toUpperCase(Locale.ROOT));
            field("Goal total XP", ""+c.goalXp,y+26,v-> {
                double n=Double.parseDouble(v); if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException(); c.goalXp=n;
            });
            field("Goal date (YYYY-MM-DD)",c.goalDate,y+52,v-> {
                if(!v.isEmpty())java.time.LocalDate.parse(v); c.goalDate=v;
            });
            field("Item / collection ID",c.lookup,y+78,v->c.lookup=v.toUpperCase(Locale.ROOT));
            field("Goal level (0 uses total XP)",""+c.goalLevel,y+104,v->c.goalLevel=Math.clamp(Integer.parseInt(v),0,60));
        }
        if(page==2) {
            field("Price alerts ID=coins;...",pairs(c.priceAlerts),y,v->c.priceAlerts=doubles(v));
            field("Event timers name=epoch-ms;...",pairs(c.timers),y+26,v->c.timers=longs(v));
            field("Local sacks ID=count;...",pairs(c.sackCounts),y+52,v->c.sackCounts=longs(v));
            addRenderableWidget(Button.builder(Component.literal("Reset session"),b->mod.context.session.reset()).bounds(width/2,y+78,140,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"),b-> {
            page=(page+2)%3; rebuildWidgets();
        }).bounds(10,height-28,30,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b-> {
            page=(page+1)%3; rebuildWidgets();
        }).bounds(44,height-28,30,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save / done"),b->onClose()).bounds(width-110,height-28,100,20).build());
    }
    private static String pairs(Map<String,? extends Number> m) {
        return String.join(";",m.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());
    }
    private static Map<String,Double> doubles(String s) {
        var m=new LinkedHashMap<String,Double>();
        if(!s.isBlank())for(String pair:s.split(";")) {
            String[] p=pair.split("=",2);
            double n=Double.parseDouble(p[1]);
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException();
            m.put(p[0],n);
        }
        return m;
    }
    private static Map<String,Long> longs(String s) {
        var m=new LinkedHashMap<String,Long>();
        doubles(s).forEach((k,v)->m.put(k,v.longValue()));
        return m;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        g.fill(0,0,width,height,0xF00B111C);
        g.text(font,"SkyDash settings / "+(page+1)+" of 3",10,12,0xFF55DCC2);
        g.text(font,"API key: edit config/skydash.json; never shown here",10,30,0xFF9FAEBE);
        for(int i=0; i<labels.size(); i++)g.text(font,font.plainSubstrByWidth(labels.get(i),width/2-18),10,61+i*26,0xFFE0E8EF);
        g.text(font,message,10,height-45,0xFFFFA477);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose() {
        mod.save();
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() {
        return false;
    }
}
