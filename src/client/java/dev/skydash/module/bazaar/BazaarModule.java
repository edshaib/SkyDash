package dev.skydash.module.bazaar;
import dev.skydash.*;
import dev.skydash.module.*;
import java.util.*;
public final class BazaarModule extends BaseModule {
    private final Set<String> triggered=new HashSet<>();
    public BazaarModule(DashboardContext c) {
        super(c,"bazaar","Bazaar HUD");
    }
    public void tick() {
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/bazaar"));
        for(var id:ctx.inventory().keySet()) {
            double buy=ctx.buy(id),sell=ctx.sell(id);
            if(buy>0)out.add(ctx.name(id)+": spread "+fmt(buy-sell)+" ("+fmt((buy-sell)/buy*100)+"%)");
        }
        for(var e:ctx.config.priceAlerts.entrySet()) {
            double price=ctx.buy(e.getKey());
            if(price>0&&price<=e.getValue()) {
                out.add("ALERT: "+ctx.name(e.getKey())+" <= "+fmt(e.getValue()));
                if(triggered.add(e.getKey()))ctx.notify("Price alert: "+ctx.name(e.getKey())+" "+fmt(price));
            } else triggered.remove(e.getKey());
        }
        if(out.size()==1)out.add("Hold a Bazaar item; no margin observed");
        out.add("Gross spread before taxes / fees");
        content=List.copyOf(out);
    }
}
