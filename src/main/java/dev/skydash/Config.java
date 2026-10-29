package dev.skydash;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class Config {
    public String apiKey="", proxyUrl="", profileId="";
    public int profileRefreshSeconds=180, publicRefreshSeconds=60;
    public Map<String,ModuleConfig> modules=new LinkedHashMap<>();
    public Map<String,Double> priceAlerts=new HashMap<>();
    public String goalSkill="SKILL_FARMING", goalDate="", lookup="WHEAT";
    public double goalXp=0;
    public int goalLevel=0;
    public Map<String,Long> timers=new LinkedHashMap<>();
    public Map<String,Long> sackCounts=new HashMap<>();
    public ModuleConfig module(String id) {
        return modules.computeIfAbsent(id,k->new ModuleConfig());
    }
    public int profileTtl() {
        return Math.clamp(profileRefreshSeconds,120,300);
    }
    public int publicTtl() {
        return Math.clamp(publicRefreshSeconds,30,3600);
    }
    public static final class ModuleConfig {
        public boolean enabled=true,pinned=false;
        public int x=8,y=8;
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static Config load(Path path) {
        try {
            Config c=GSON.fromJson(Files.readString(path),Config.class);
            return c==null?new Config():c.normalize();
        } catch(Exception e) {
            return new Config();
        }
    }
    private Config normalize() {
        if(apiKey==null)apiKey="";
        if(proxyUrl==null)proxyUrl="";
        if(profileId==null)profileId="";
        if(modules==null)modules=new LinkedHashMap<>();
        modules.entrySet().removeIf(e->e.getValue()==null);
        if(priceAlerts==null)priceAlerts=new HashMap<>();
        priceAlerts.entrySet().removeIf(e->e.getValue()==null||!Double.isFinite(e.getValue())||e.getValue()<0);
        if(timers==null)timers=new LinkedHashMap<>();
        timers.entrySet().removeIf(e->e.getValue()==null);
        if(sackCounts==null)sackCounts=new HashMap<>();
        sackCounts.entrySet().removeIf(e->e.getValue()==null||e.getValue()<0);
        if(goalSkill==null)goalSkill="SKILL_FARMING";
        if(goalDate==null)goalDate="";
        if(lookup==null)lookup="WHEAT";
        profileRefreshSeconds=profileTtl();
        publicRefreshSeconds=publicTtl();
        return this;
    }
    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(tmp,GSON.toJson(this));
            try {
                Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException e) {
                Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(Exception ignored) {
        }
    }
}
