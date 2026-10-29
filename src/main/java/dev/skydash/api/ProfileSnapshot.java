package dev.skydash.api;
import com.google.gson.*;
import java.util.*;
public record ProfileSnapshot(Map<String,Double> skills,Map<String,Double> collections,Map<String,Double> sacks,double purse,JsonObject member) {
    public static ProfileSnapshot parse(JsonObject root,String uuid) {
        JsonObject profile=Json.obj(root,"profile"), members=Json.obj(profile,"members");
        if(!members.has(uuid)||!members.get(uuid).isJsonObject())throw new IllegalArgumentException("Own profile member unavailable");
        JsonObject m=members.getAsJsonObject(uuid);
        return new ProfileSnapshot(numbers(Json.obj(Json.obj(m,"player_data"),"experience")),numbers(Json.obj(m,"collection")),numbers(Json.obj(Json.obj(m,"inventory"),"sacks_counts")),Json.num(Json.obj(m,"currencies"),"coin_purse"),m.deepCopy());
    }
    private static Map<String,Double> numbers(JsonObject o) {
        var r=new TreeMap<String,Double>();
        o.entrySet().forEach(e-> {
            try {
                double n=e.getValue().getAsDouble(); if(Double.isFinite(n))r.put(e.getKey(),n);
            } catch(RuntimeException ignored) {
            }
        });
        return Collections.unmodifiableMap(r);
    }
}
