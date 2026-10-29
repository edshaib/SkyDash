package dev.skydash.module.garden;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import com.google.gson.*;
import java.util.*;
public final class GardenModule extends BaseModule {
    private final Map<String,Double> baseline=new HashMap<>();
    private long baselineTime;
    private String profile="";
    public GardenModule(DashboardContext c) {
        super(c,"garden","Garden");
    }
    public void tick() {
        if(!profile.equals(ctx.api.ownProfileId())) {
            profile=ctx.api.ownProfileId();
            baseline.clear();
            baselineTime=0;
        }
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
        if(baselineTime==0) {
            baselineTime=cached.updated().toEpochMilli();
            Json.obj(g,"resources_collected").entrySet().forEach(e->baseline.put(e.getKey(),e.getValue().getAsDouble()));
        }
        double elapsed=(cached.updated().toEpochMilli()-baselineTime)/3600000.0,value=0;
        for(var e:Json.obj(g,"resources_collected").entrySet())value+=Math.max(0,e.getValue().getAsDouble()-baseline.getOrDefault(e.getKey(),e.getValue().getAsDouble()))*ctx.sell(e.getKey());
        out.add(ctx.api.cached("skyblock/bazaar")==null?"Coins/h unavailable: Bazaar prices missing":elapsed>0?"Crop revenue estimate: "+fmt(value/elapsed)+" coins/h":"Coins/h: waiting for another Garden snapshot");
        out.add("Milestone totals; level thresholds unavailable");
        out.add("Profile: "+ctx.api.age("skyblock/profile"));
        content=List.copyOf(out);
    }
}
