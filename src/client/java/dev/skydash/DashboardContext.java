package dev.skydash;
import com.google.gson.*;
import dev.skydash.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class DashboardContext {
    public final Config config;
    public final ApiClient api;
    public final SessionTracker session=new SessionTracker();
    public final Map<String,JsonObject> items=new TreeMap<>(),collections=new TreeMap<>();
    private Object itemsVersion,collectionVersion;
    public DashboardContext(Config c,ApiClient a) {
        config=c;
        api=a;
    }
    public JsonObject data(String path) {
        var e=api.cached(path);
        return e==null?new JsonObject():e.value();
    }
    public void tick() {
        var it=api.cached("resources/skyblock/items");
        if(it!=null&&it!=itemsVersion) {
            itemsVersion=it;
            items.clear();
            for(var e:Json.arr(it.value(),"items"))if(e.isJsonObject()) {
                JsonObject o=e.getAsJsonObject();
                items.put(Json.str(o,"id",""),o);
            }
        }
        var co=api.cached("resources/skyblock/collections");
        if(co!=null&&co!=collectionVersion) {
            collectionVersion=co;
            collections.clear();
            for(var category:Json.obj(co.value(),"collections").entrySet())if(category.getValue().isJsonObject())for(var e:Json.obj(category.getValue().getAsJsonObject(),"items").entrySet())if(e.getValue().isJsonObject())collections.put(e.getKey(),e.getValue().getAsJsonObject());
        }
        session.tick();
        if(session.active())observeSacks();
    }
    private void observeSacks() {
        var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)||!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("sack"))return;
        // Read only visible server-supplied lore, never click or open a menu automatically.
        for(var slot:screen.getMenu().slots) {
            if(slot.container==mc.player.getInventory())continue;
            var stack=slot.getItem();
            if(stack.isEmpty())continue;
            var lore=stack.get(DataComponents.LORE);
            if(lore==null)continue;
            String id=itemId(stack);
            if(id.isEmpty()||!items.containsKey(id)) {
                String name=stack.getHoverName().getString().replaceAll("§.","");
                id=items.entrySet().stream().filter(e->Json.str(e.getValue(),"name","").replaceAll("§.","").equals(name)).map(Map.Entry::getKey).findFirst().orElse("");
            }
            if(id.isEmpty())continue;
            for(var line:lore.lines()) {
                var matcher=java.util.regex.Pattern.compile("(?:Stored|Amount):\\s*([\\d,]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line.getString().replaceAll("§.",""));
                if(matcher.find())try {
                    config.sackCounts.put(id,Long.parseLong(matcher.group(1).replace(",","")));
                } catch(NumberFormatException ignored) {
                }
            }
        }
    }
    public String itemId(ItemStack stack) {
        if(stack.isEmpty())return "";
        var d=stack.get(DataComponents.CUSTOM_DATA);
        if(d==null)return "";
        var tag=d.copyTag();
        String id=tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");
        return id.isEmpty()?tag.getStringOr("id",""):id;
    }
    public Map<String,Long> inventory() {
        var mc=Minecraft.getInstance();
        Map<String,Long> out=new TreeMap<>();
        if(mc.player==null)return out;
        var inventory=mc.player.getInventory();
        for(int i=0; i<inventory.getContainerSize(); i++) {
            var s=inventory.getItem(i);
            String id=itemId(s);
            if(!id.isEmpty())out.merge(id,(long)s.getCount(),Long::sum);
        }
        return out;
    }
    public String name(String id) {
        return Json.str(items.get(id),"name",id);
    }
    public double buy(String id) {
        return Json.num(Json.obj(Json.obj(Json.obj(data("skyblock/bazaar"),"products"),id),"quick_status"),"buyPrice");
    }
    public double sell(String id) {
        return Json.num(Json.obj(Json.obj(Json.obj(data("skyblock/bazaar"),"products"),id),"quick_status"),"sellPrice");
    }
    public void notify(String message) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null)mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("[SkyDash] "+message));
    }
}
