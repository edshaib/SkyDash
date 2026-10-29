package dev.skydash.module.news;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import java.util.*;
public final class NewsModule extends BaseModule {
    private final Set<String> alerted=new HashSet<>();
    public NewsModule(DashboardContext c) {
        super(c,"news","Fire sales & news");
    }
    public void tick() {
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/firesales"));
        long now=System.currentTimeMillis();
        for(var e:Json.arr(ctx.data("skyblock/firesales"),"sales")) {
            if(!e.isJsonObject())continue;
            var sale=e.getAsJsonObject();
            long start=(long)Json.num(sale,"start"),end=(long)Json.num(sale,"end");
            String id=Json.str(sale,"item_id","Unknown");
            if(end<now)continue;
            out.add(ctx.name(id)+(start>now?": in "+((start-now)/60000)+"m":": live"));
            if(start>now&&start-now<=300000&&alerted.add(id+start))ctx.notify("Fire sale in "+Math.max(1,(start-now)/60000)+"m: "+ctx.name(id));
        }
        var news=Json.arr(ctx.data("skyblock/news"),"items");
        if(!news.isEmpty()) {
            int index=(int)((now/10000)%news.size());
            out.add(Json.str(news.get(index).getAsJsonObject(),"title","News unavailable"));
        } else out.add("News unavailable");
        content=List.copyOf(out);
    }
}
