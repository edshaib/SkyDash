package dev.skydash.module.garden;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import com.google.gson.*;
import java.util.*;
public final class GardenModule extends BaseModule {
    public GardenModule(DashboardContext c) {
        super(c,"garden","Garden");
    }
    public void tick() {
        var cached=ctx.api.cached("skyblock/garden");
        if(cached==null) {
            lines("unavailable: own Garden snapshot pending",ctx.api.status());
            return;
        }
        var g=Json.obj(cached.value(),"garden");
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/garden"));
        var visitors=new ArrayList<>(Json.obj(g,"active_commissions").entrySet());
        visitors.sort(Comparator.comparingDouble(e->e.getValue().isJsonObject()?Json.num(e.getValue().getAsJsonObject(),"position"):99));
        out.add("Visitor queue: "+visitors.size());
        for(var e:visitors)if(e.getValue().isJsonObject())out.add("#"+(int)Json.num(e.getValue().getAsJsonObject(),"position")+" "+e.getKey()+" / "+Json.str(e.getValue().getAsJsonObject(),"status","unknown"));
        out.add("Unlocked plots: "+Json.arr(g,"unlocked_plots_ids").size());
        for(var id:Json.arr(g,"unlocked_plots_ids"))out.add("Plot "+id.getAsString()+": unlocked");
        Json.obj(g,"resources_collected").entrySet().forEach(e->out.add(e.getKey()+" milestone crops: "+fmt(e.getValue().getAsDouble())));
        double localValue=0;
        for(var crop:Json.obj(g,"resources_collected").keySet()) {
            long observed=ctx.session.items.getOrDefault(ctx.name(crop),0L);
            localValue+=observed*ctx.sell(crop);
        }
        out.add(localValue>0?"Observed crop value: "+fmt(localValue/ctx.session.hours())+" coins/h":"Crop earnings: waiting for local crop observations");
        out.add("Milestone totals; level thresholds unavailable");
        out.add("Profile: "+ctx.api.age("skyblock/profile"));
        content=List.copyOf(out);
    }
}
