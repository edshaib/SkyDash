package dev.skydash.module.bingo;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import com.google.gson.*;
import java.util.*;
public final class BingoModule extends BaseModule {
    public BingoModule(DashboardContext c) {
        super(c,"bingo","Bingo");
    }
    public void tick() {
        var cached=ctx.api.cached("skyblock/bingo");
        var board=ctx.data("resources/skyblock/bingo");
        if(cached==null) {
            lines("unavailable: own Bingo snapshot pending",ctx.api.status());
            return;
        }
        if(!board.has("id")) {
            lines("Event goal catalog unavailable",ctx.api.age("skyblock/bingo"));
            return;
        }
        int event=(int)Json.num(board,"id");
        var own=new JsonObject();
        for(var e:Json.arr(cached.value(),"events"))if(e.isJsonObject()&&(int)Json.num(e.getAsJsonObject(),"key")==event)own=e.getAsJsonObject();
        Set<String> completed=new HashSet<>();
        for(var e:Json.arr(own,"completed_goals"))completed.add(e.getAsString());
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/bingo"));
        out.add(Json.str(board,"name","Bingo")+": "+completed.size()+" completed");
        long now=System.currentTimeMillis();
        out.add(now<Json.num(board,"start")?"Upcoming event":now>Json.num(board,"end")?"Event ended":"Event active");
        for(var e:Json.arr(board,"goals")) {
            var goal=e.getAsJsonObject();
            String id=Json.str(goal,"id","");
            String name=Json.str(goal,"name",id);
            if(completed.contains(id))out.add("Done: "+name);
            else if(goal.has("tiers"))out.add(name+": community "+fmt(Json.num(goal,"progress")));
            else out.add("Pending: "+name);
            out.add("  "+Json.str(goal,"lore","").replaceAll("§.",""));
        }
        content=List.copyOf(out);
    }
}
