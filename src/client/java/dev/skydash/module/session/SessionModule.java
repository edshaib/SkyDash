package dev.skydash.module.session;
import dev.skydash.*;
import dev.skydash.module.*;
import java.util.*;
public final class SessionModule extends BaseModule {
    public SessionModule(DashboardContext c) {
        super(c,"session","Session stats");
    }
    public void tick() {
        var s=ctx.session;
        var out=new ArrayList<String>();
        out.add("Purse net change: "+fmt(s.coins));
        out.add("Observed XP: "+fmt(s.xp)+" ("+fmt(s.xp/s.hours())+"/h)");
        out.add("Session: "+(int)(s.hours()*60)+" min");
        s.items.forEach((id,n)->out.add(id+" +"+n));
        out.add("Chat observations; purse includes spending");
        content=List.copyOf(out);
    }
}
