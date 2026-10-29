package dev.skydash.module.museum;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import com.google.gson.*;
import java.util.*;
public final class MuseumModule extends BaseModule {
    public MuseumModule(DashboardContext c) {
        super(c,"museum","Museum");
    }
    public void tick() {
        var cache=ctx.api.cached("skyblock/museum");
        if(cache==null) {
            lines("unavailable: own Museum snapshot pending",ctx.api.status());
            return;
        }
        JsonObject root=cache.value();
        JsonObject own=Json.obj(Json.obj(root,"members"),ctx.api.ownUuid());
        if(own.isEmpty()) {
            lines("Museum unavailable; check in-game API settings",ctx.api.age("skyblock/museum"));
            return;
        }
        JsonObject donated=Json.obj(own,"items");
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/museum"));
        out.add("Recorded donations: "+donated.size());
        for(var e:ctx.items.entrySet()) {
            var item=e.getValue();
            if(!item.has("museum_data")||donated.has(e.getKey()))continue;
            boolean mapped=false;
            for(var id:Json.arr(Json.obj(item,"museum_data"),"mapped_item_ids"))if(donated.has(id.getAsString()))mapped=true;
            if(mapped)continue;
            double cost=ctx.buy(e.getKey());
            out.add(ctx.name(e.getKey())+": "+(cost>0?fmt(cost)+" coins":"Bazaar price unavailable"));
        }
        if(ctx.items.isEmpty())out.add("Donation catalog unavailable");
        content=List.copyOf(out);
    }
}
