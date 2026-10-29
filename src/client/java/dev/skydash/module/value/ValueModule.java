package dev.skydash.module.value;
import dev.skydash.*;
import dev.skydash.module.*;
public final class ValueModule extends BaseModule {
    public ValueModule(DashboardContext c) {
        super(c,"value","Inventory value");
    }
    public void tick() {
        if(ctx.api.cached("skyblock/bazaar")==null) {
            lines("unavailable: Bazaar prices pending");
            return;
        }
        double inv=0,sacks=0;
        int unknown=0;
        for(var e:ctx.inventory().entrySet()) {
            double price=ctx.sell(e.getKey());
            if(price<=0)unknown++;
            else inv+=price*e.getValue();
        }
        for(var e:ctx.config.sackCounts.entrySet())sacks+=ctx.sell(e.getKey())*e.getValue();
        lines("Inventory instant sell estimate: "+fmt(inv),"Sacks (last observed / saved counts): "+fmt(sacks),"Unpriced item types: "+unknown,ctx.api.age("skyblock/bazaar"),"Bazaar items only; excludes upgrades / tax");
    }
}
