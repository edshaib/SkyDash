package dev.skydash;
import dev.skydash.api.*;
import dev.skydash.module.*;
import dev.skydash.module.bazaar.*;
import dev.skydash.module.news.*;
import dev.skydash.module.info.*;
import dev.skydash.module.session.*;
import dev.skydash.module.timers.*;
import dev.skydash.module.value.*;
import dev.skydash.module.settings.*;
import dev.skydash.ui.*;
import dev.skydash.module.garden.GardenModule;
import dev.skydash.module.skills.SkillsModule;
import dev.skydash.module.museum.MuseumModule;
import dev.skydash.module.collections.CollectionsModule;
import dev.skydash.module.bingo.BingoModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.*;
public final class SkyDash implements ClientModInitializer {
    public static SkyDash INSTANCE;
    public Config config;
    public ApiClient api;
    public DashboardContext context;
    public List<dev.skydash.module.Module> modules;
    private Path path;
    private int ticks;
    private boolean wasActive;
    private KeyMapping open;
    @Override public void onInitializeClient() {
        INSTANCE=this;
        path=FabricLoader.getInstance().getConfigDir().resolve("skydash.json");
        config=Config.load(path);
        api=new ApiClient(path.resolveSibling("skydash-request-budget.txt"));
        context=new DashboardContext(config,api);
        modules=new ArrayList<>(List.of(new BazaarModule(context),new NewsModule(context),new InfoModule(context),new SessionModule(context),new TimersModule(context),new ValueModule(context),new SettingsModule(context),new GardenModule(context),new SkillsModule(context),new MuseumModule(context),new CollectionsModule(context),new BingoModule(context)));
        for(int i=0; i<modules.size(); i++) {
            var c=modules.get(i).config();
            if(c.x==8&&c.y==8)c.y=8+i*90;
        }
        save();
        open=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skydash.open",79,KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc-> {
            while(open.consumeClick())mc.setScreen(new DashboardScreen(mc.screen)); if(++ticks%20!=0)return; try {
                context.tick(); if(context.session.active()) {
                    wasActive=true; api.identity(mc.getUser().getProfileId().toString()); api.tick(config); for(var module:modules)if(module.enabled())try {
                        module.tick();
                    } catch(RuntimeException ignored) {
                    }
                } else {
                    if(wasActive)context.session.rebasePurse(); wasActive=false;
                }
            } catch(RuntimeException ignored) {
            }
        });
        ClientReceiveMessageEvents.GAME.register((message,overlay)-> {
            try {
                context.session.message(message.getString(),overlay);
            } catch(RuntimeException ignored) {
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skydash","dashboard"),(g,delta)-> {
            if(!context.session.active()||Minecraft.getInstance().options.hideGui)return; for(var module:modules)if(module.enabled()&&module.config().pinned)try {
                module.render(g,Math.clamp(module.config().x,0,Math.max(0,g.guiWidth()-210)),Math.clamp(module.config().y,0,Math.max(0,g.guiHeight()-65)),210,3);
            } catch(RuntimeException ignored) {
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc-> {
            save(); api.close();
        });
    }
    public void save() {
        config.save(path);
    }
}
