package dev.skydash.module.info;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import java.util.*;
public final class InfoModule extends BaseModule {
    public InfoModule(DashboardContext c) {
        super(c,"info","Item & collection info");
    }
    public void tick() {
        String id=ctx.config.lookup.toUpperCase(Locale.ROOT);
        var out=new ArrayList<String>();
        var item=ctx.items.get(id);
        out.add(ctx.name(id)+" / "+Json.str(item,"tier","tier unavailable"));
        var col=ctx.collections.get(id);
        if(col!=null)for(var e:Json.arr(col,"tiers")) {
            var t=e.getAsJsonObject();
            out.add("Tier "+(int)Json.num(t,"tier")+": "+fmt(Json.num(t,"amountRequired")));
        } else out.add("Collection thresholds unavailable");
        out.add(ctx.api.age("resources/skyblock/items"));
        content=List.copyOf(out);
    }
}
