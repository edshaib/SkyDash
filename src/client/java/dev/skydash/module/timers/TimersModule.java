package dev.skydash.module.timers;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import java.util.*;
public final class TimersModule extends BaseModule {
    public TimersModule(DashboardContext c) {
        super(c,"timers","Timers");
    }
    public static String remaining(long ms) {
        if(ms<=0)return "now / elapsed";
        long sec=ms/1000;
        return sec/3600+"h "+sec/60%60+"m "+sec%60+"s";
    }
    public void tick() {
        long now=System.currentTimeMillis();
        long hour=3600000;
        long start=Math.floorDiv(now,hour)*hour+900000;
        boolean active=now>=start&&now<start+1200000;
        if(!active&&start<=now)start+=hour;
        var out=new ArrayList<String>();
        out.add("Mayor: "+Json.str(Json.obj(ctx.data("resources/skyblock/election"),"mayor"),"name","unavailable"));
        out.add(active?"Jacob ends: "+remaining(start+1200000-now):"Jacob starts: "+remaining(start-now));
        var election=Json.obj(ctx.data("resources/skyblock/election"),"current");
        long end=(long)Json.num(election,"end");
        out.add(end>0?"Election: "+remaining(end-now):"Election countdown: unavailable");
        ctx.config.timers.forEach((name,time)->out.add(name+": "+remaining(time-now)));
        long epoch=1560275700000L,year=446400000L,day=1200000L;
        long yearStart=epoch+Math.floorDiv(now-epoch,year)*year;
        long mayor=yearStart+88*day;
        if(mayor<=now)mayor+=year;
        out.add("Mayor change (calendar estimate): "+remaining(mayor-now));
        long spooky=yearStart+245*day;
        if(spooky<=now)spooky+=year;
        out.add("Spooky Festival (calendar): "+remaining(spooky-now));
        long newYear=yearStart+year-3*day;
        if(newYear<=now)newYear+=year;
        out.add("New Year event (calendar): "+remaining(newYear-now));
        out.add("Clock estimates; custom deadlines also listed");
        content=List.copyOf(out);
    }
}
