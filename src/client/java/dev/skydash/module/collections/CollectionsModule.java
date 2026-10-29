package dev.skydash.module.collections;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import java.util.*;
public final class CollectionsModule extends BaseModule {
    public CollectionsModule(DashboardContext c) {
        super(c,"collections","Collections");
    }
    private record Next(String text,double ratio) {
    }
    public void tick() {
        var p=ctx.api.ownProfile();
        if(p.isEmpty()) {
            lines("unavailable: own profile pending",ctx.api.status());
            return;
        }
        var ranked=new ArrayList<Next>();
        for(var e:p.get().collections().entrySet()) {
            var meta=ctx.collections.get(e.getKey());
            if(meta==null)continue;
            for(var t:Json.arr(meta,"tiers")) {
                var tier=t.getAsJsonObject();
                double target=Json.num(tier,"amountRequired");
                if(target>e.getValue()) {
                    ranked.add(new Next(Json.str(meta,"name",e.getKey())+" T"+(int)Json.num(tier,"tier")+": "+fmt(target-e.getValue())+" left ("+fmt(e.getValue()/target*100)+"%)",e.getValue()/target));
                    break;
                }
            }
        }
        ranked.sort(Comparator.comparingDouble(Next::ratio).reversed());
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/profile"));
        ranked.forEach(n->out.add(n.text));
        if(ranked.isEmpty())out.add("No next tier available / collection API disabled");
        content=List.copyOf(out);
    }
}
