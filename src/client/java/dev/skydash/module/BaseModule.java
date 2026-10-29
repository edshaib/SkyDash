package dev.skydash.module;
import dev.skydash.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;
public abstract class BaseModule implements Module {
    protected final DashboardContext ctx;
    private final String id,name;
    protected volatile List<String> content=List.of("unavailable");
    protected BaseModule(DashboardContext ctx,String id,String name) {
        this.ctx=ctx;
        this.id=id;
        this.name=name;
        ctx.config.module(id);
    }
    public String id() {
        return id;
    }
    public String name() {
        return name;
    }
    public Config.ModuleConfig config() {
        return ctx.config.module(id);
    }
    public boolean enabled() {
        return config().enabled;
    }
    public List<String> lines() {
        return content;
    }
    public void render(GuiGraphicsExtractor g,int x,int y,int width,int maxLines) {
        var f=Minecraft.getInstance().font;
        int count=Math.min(maxLines,content.size());
        g.fill(x,y,x+width,y+23+count*12,0xD9101827);
        g.fill(x,y,x+2,y+23+count*12,0xFF55DCC2);
        g.text(f,f.plainSubstrByWidth(name,width-12),x+7,y+6,0xFF55DCC2);
        for(int i=0; i<count; i++)g.text(f,f.plainSubstrByWidth(content.get(i),width-12),x+7,y+21+i*12,0xFFE0E8EF);
    }
    protected void lines(String...lines) {
        content=List.of(lines);
    }
    protected static String fmt(double n) {
        return String.format(Locale.ROOT,"%,.1f",n);
    }
}
