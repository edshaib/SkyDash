package dev.skydash.module.skills;
import dev.skydash.*;
import dev.skydash.api.*;
import dev.skydash.module.*;
import dev.skydash.module.timers.TimersModule;
import java.util.*;
import java.time.*;
public final class SkillsModule extends BaseModule {
    public SkillsModule(DashboardContext c) {
        super(c,"skills","Skills & XP");
    }
    public void tick() {
        var opt=ctx.api.ownProfile();
        if(opt.isEmpty()) {
            lines("unavailable: own profile pending",ctx.api.status());
            return;
        }
        var p=opt.get();
        var out=new ArrayList<String>();
        out.add(ctx.api.age("skyblock/profile"));
        for(var e:p.skills().entrySet()) {
            String skill=e.getKey().replace("SKILL_","");
            double rate=ctx.session.skillXp.getOrDefault(skill,0.0)/ctx.session.hours();
            int level=0;
            double next=0;
            var meta=Json.obj(Json.obj(ctx.data("resources/skyblock/skills"),"skills"),skill);
            for(var l:Json.arr(meta,"levels")) {
                var o=l.getAsJsonObject();
                double threshold=Json.num(o,"totalExpRequired");
                if(e.getValue()>=threshold)level=(int)Json.num(o,"level");
                else {
                    next=threshold;
                    break;
                }
            }
            out.add(skill+": "+fmt(e.getValue())+" XP / level "+level);
            out.add("  "+fmt(rate)+" XP/h; next "+(next>0?(rate>0?TimersModule.remaining((long)((next-e.getValue())/rate*3600000)):"ETA unavailable"):"max / thresholds unavailable"));
        }
        if(p.skills().isEmpty())out.add("Skills unavailable; check in-game API settings");
        String skill=ctx.config.goalSkill;
        double target=ctx.config.goalXp;
        if(ctx.config.goalLevel>0)for(var e:Json.arr(Json.obj(Json.obj(ctx.data("resources/skyblock/skills"),"skills"),skill.replace("SKILL_","")),"levels"))if((int)Json.num(e.getAsJsonObject(),"level")==ctx.config.goalLevel)target=Json.num(e.getAsJsonObject(),"totalExpRequired");
        double current=p.skills().getOrDefault(skill,0.0),remaining=Math.max(0,target-current);
        out.add("Goal "+skill+": "+fmt(remaining)+" XP remaining");
        if(!ctx.config.goalDate.isBlank())try {
            long ms=LocalDate.parse(ctx.config.goalDate).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-System.currentTimeMillis();
            out.add(ms>0?"Need "+fmt(remaining/(ms/86400000.0))+" XP/day by "+ctx.config.goalDate:"Goal date passed");
        } catch(RuntimeException ignored) {
        }
        content=List.copyOf(out);
    }
}
