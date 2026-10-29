package dev.skydash.api;
import com.google.gson.*;
import dev.skydash.Config;
import java.net.*;
import java.nio.file.Path;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Sole network owner. Modules only inspect immutable cache snapshots. No key in URLs or messages. */
public final class ApiClient implements AutoCloseable {
    public record ApiResponse(int statusCode,HttpHeaders headers,String body) {
    }
    @FunctionalInterface public interface Transport {
        ApiResponse send(HttpRequest request) throws Exception;
    }
    private final Transport transport;
    public static final List<String> PUBLIC=List.of("skyblock/bazaar","skyblock/firesales","skyblock/news","resources/skyblock/items","resources/skyblock/collections","resources/skyblock/election","resources/skyblock/skills","resources/skyblock/bingo");
    private static final Set<String> PRIVATE=Set.of("skyblock/profile","skyblock/profiles","skyblock/garden","skyblock/museum","skyblock/bingo");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r-> {
        Thread t=new Thread(r,"SkyDash-API"); t.setDaemon(true); return t;
    });
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Clock clock;
    private final RequestBudget budget;
    private final ConcurrentMap<String,CacheEntry<JsonObject>> cache=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Instant> retries=new ConcurrentHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private volatile Instant blockedUntil=Instant.EPOCH,manualAt=Instant.EPOCH;
    private volatile boolean authStopped, needsCredentials;
    private volatile String status="Ready";
    private volatile String uuid="",profile="",credentials="";
    private volatile int privateTurn;
    public ApiClient() {
        this(Clock.systemUTC());
    }
    public ApiClient(Path ledger) {
        this(Clock.systemUTC(),null,ledger);
    }
    public ApiClient(Clock c) {
        this(c,null);
    }
    public ApiClient(Clock c,Transport transport) {
        this(c,transport,null);
    }
    public ApiClient(Clock c,Transport transport,Path ledger) {
        clock=c;
        budget=new RequestBudget(c,ledger);
        this.transport=transport==null?request-> {
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(),response.headers(),response.body());
        }
        :transport;
    }
    /** Test / shutdown barrier; never waited on by the game thread. */
    public CompletionStage<Void> idle() {
        return CompletableFuture.runAsync(()-> {
        },worker);
    }
    public String status() {
        return needsCredentials?"Own-profile modules require an API key or HTTPS proxy":authStopped?"API authorization rejected; update credentials to resume":status;
    }
    public CacheEntry<JsonObject> cached(String endpoint) {
        return cache.get(endpoint);
    }
    public String age(String endpoint) {
        var c=cached(endpoint);
        return c==null?"unavailable":c.age(clock.instant());
    }
    public synchronized void identity(String playerUuid) {
        String next=playerUuid.replace("-","");
        if(!uuid.equals(next)) {
            uuid=next;
            profile="";
            clearPrivate();
        }
    }
    public synchronized void resetCredentials() {
        authStopped=false;
        credentials="";
        clearPrivate();
        status="Settings changed";
    }
    private void clearPrivate() {
        cache.keySet().removeIf(PRIVATE::contains);
        retries.keySet().removeIf(PRIVATE::contains);
    }
    public synchronized boolean manualRefresh(Config config) {
        Instant now=clock.instant();
        if(now.isBefore(manualAt.plusSeconds(30))) {
            status="Manual refresh cooldown: 30 seconds";
            return false;
        }
        manualAt=now;
        retries.clear();
        pump(config,true);
        return true;
    }
    public void tick(Config config) {
        pump(config,false);
    }
    private synchronized void pump(Config c,boolean manual) {
        // Snapshot mutable settings on the client thread, never read the config from worker code.
        String key=c.apiKey==null?"":c.apiKey.trim(),proxy=c.proxyUrl==null?"":c.proxyUrl.trim();
        String stamp=key+"\n"+proxy;
        if(!stamp.equals(credentials)) {
            credentials=stamp;
            authStopped=false;
            clearPrivate();
        }
        for(String endpoint:PUBLIC)schedule(endpoint,"",false,key,proxy,Duration.ofSeconds(c.publicTtl()),false);
        boolean ownModules=java.util.stream.Stream.of("garden","skills","museum","collections","bingo").anyMatch(id->c.module(id).enabled);
        needsCredentials=ownModules&&key.isEmpty()&&proxy.isEmpty();
        if(!ownModules||uuid.isEmpty()||(key.isEmpty()&&proxy.isEmpty())||authStopped)return;
        String requested=c.profileId==null?"":c.profileId.replace("-","").trim();
        if(profile.isEmpty()||(!requested.isEmpty()&&!requested.equals(profile))) {
            // Discover and validate membership before any profile-specific request.
            schedule("skyblock/profiles","uuid="+uuid,true,key,proxy,Duration.ofSeconds(c.profileTtl()),manual);
            var entry=cache.get("skyblock/profiles");
            if(entry==null)return;
            String selected="";
            JsonObject selectedData=null;
            for(JsonElement el:Json.arr(entry.value(),"profiles")) {
                if(!el.isJsonObject())continue;
                JsonObject p=el.getAsJsonObject();
                String id=Json.str(p,"profile_id","").replace("-","");
                if(!id.matches("[a-fA-F0-9]{32}"))continue;
                if(!Json.obj(p,"members").has(uuid))continue;
                if(!requested.isEmpty()?requested.equals(id):(selected.isEmpty()||Json.bool(p,"selected"))) {
                    selected=id;
                    selectedData=p;
                }
            }
            if(selected.isEmpty()) {
                status="Own profile unavailable; check profileId";
                return;
            }
            profile=selected;
            cache.keySet().removeIf(e->PRIVATE.contains(e)&&!e.equals("skyblock/profiles"));
            if(selectedData!=null) {
                JsonObject shared=new JsonObject();
                shared.add("profile",selectedData.deepCopy());
                cache.put("skyblock/profile",new CacheEntry<>(shared,entry.updated(),Duration.ofSeconds(c.profileTtl())));
            }
        }
        // Round robin guarantees Garden, Museum and Bingo do not starve behind profile refreshes.
        String[] endpoints= {
            "skyblock/profile","skyblock/garden","skyblock/museum","skyblock/bingo"
        };
        for(int i=0; i<endpoints.length; i++) {
            int index=(privateTurn+i)%endpoints.length;
            String endpoint=endpoints[index];
            if(endpoint.endsWith("garden")&&!c.module("garden").enabled)continue;
            if(endpoint.endsWith("museum")&&!c.module("museum").enabled)continue;
            if(endpoint.endsWith("bingo")&&!c.module("bingo").enabled)continue;
            if(endpoint.endsWith("profile")&&!java.util.stream.Stream.of("garden","skills","museum","collections").anyMatch(id->c.module(id).enabled))continue;
            String query=endpoint.endsWith("bingo")?"uuid="+uuid:"profile="+profile;
            if(schedule(endpoint,query,true,key,proxy,Duration.ofSeconds(c.profileTtl()),manual)) {
                break;
            }
        }
    }
    private boolean schedule(String endpoint,String query,boolean authenticated,String key,String proxy,Duration ttl,boolean manual) {
        Instant now=clock.instant();
        var prior=cache.get(endpoint);
        if((!manual&&prior!=null&&!prior.expired(now))||now.isBefore(retries.getOrDefault(endpoint,Instant.EPOCH))||now.isBefore(blockedUntil)||!pending.add(endpoint))return false;
        if(authenticated&&authStopped) {
            pending.remove(endpoint);
            return false;
        }
        String owner=uuid,ownProfile=profile,stamp=credentials;
        worker.execute(()-> {
            try {
                if(clock.instant().isBefore(blockedUntil)||(authenticated&&authStopped))return; if(authenticated&&(!owner.equals(uuid)||!stamp.equals(credentials)))return; URI base=URI.create(authenticated&&!proxy.isEmpty()?proxy:"https://api.hypixel.net/v2"); if(!"https".equalsIgnoreCase(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)throw new IllegalArgumentException(); String path=base.toString().replaceAll("/+$","")+"/"+endpoint+(query.isEmpty()?"":"?"+query); var builder=HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(20)).header("User-Agent","SkyDash/0.1.0").GET(); // Keys only go to the official service; proxy operators supply their own upstream credentials.
                if(authenticated&&proxy.isEmpty())builder.header("API-Key",key); if(authenticated&&!budget.acquire()) {
                    status="Request budget used or ledger unavailable; showing cached data"; retries.put(endpoint,clock.instant().plusSeconds(1)); return;
                }
                if(authenticated) {
                    int turn=List.of("skyblock/profile","skyblock/garden","skyblock/museum","skyblock/bingo").indexOf(endpoint); if(turn>=0)privateTurn=(turn+1)%4;
                }
                var response=transport.send(builder.build()); Instant finished=clock.instant(); if(authenticated&&(!owner.equals(uuid)||!stamp.equals(credentials)||(!endpoint.endsWith("profiles")&&!ownProfile.equals(profile))))return; int code=response.statusCode(); if(code==401||code==403) {
                    if(authenticated)authStopped=true; status="API authorization rejected ("+code+"); update credentials to resume"; retries.put(endpoint,finished.plusSeconds(300)); return;
                }
                if(code==429) {
                    long seconds=60; try {
                        seconds=Long.parseLong(response.headers().firstValue("RateLimit-Reset").orElse("60"));
                    } catch(NumberFormatException ignored) {
                    }
                    blockedUntil=finished.plusSeconds(Math.max(1,seconds)); status="API rate limited; cached data retained"; return;
                }
                if(code!=200)throw new IllegalStateException(); JsonObject data=JsonParser.parseString(response.body()).getAsJsonObject(); if(data.has("success")&&!Json.bool(data,"success"))throw new IllegalStateException(); Duration lifetime=authenticated?ttl:CacheEntry.maxAge(response.headers().firstValue("Cache-Control").orElse(null),ttl); if(!authenticated) {
                    long age=0; try {
                        age=Long.parseLong(response.headers().firstValue("Age").orElse("0"));
                    } catch(NumberFormatException ignored) {
                    }
                    lifetime=Duration.ofSeconds(Math.max(0,lifetime.toSeconds()-age));
                }
                if(authenticated&&response.headers().firstValue("RateLimit-Remaining").orElse("1").equals("0")) {
                    long reset=60; try {
                        reset=Long.parseLong(response.headers().firstValue("RateLimit-Reset").orElse("60"));
                    } catch(NumberFormatException ignored) {
                    }
                    blockedUntil=finished.plusSeconds(Math.max(1,reset));
                }
                cache.put(endpoint,new CacheEntry<>(data,finished,lifetime)); retries.put(endpoint,finished.plusSeconds(authenticated?1:Math.max(30,ttl.toSeconds()))); status="Ready";
            } catch(Exception e) {
                status="API unavailable; cached data retained"; retries.put(endpoint,clock.instant().plusSeconds(60));
            } finally {
                pending.remove(endpoint);
            }
        });
        return true;
    }
    public Optional<ProfileSnapshot> ownProfile() {
        var c=cache.get("skyblock/profile");
        if(c==null)return Optional.empty();
        try {
            return Optional.of(ProfileSnapshot.parse(c.value(),uuid));
        } catch(RuntimeException e) {
            return Optional.empty();
        }
    }
    public String ownUuid() {
        return uuid;
    }
    public String ownProfileId() {
        return profile;
    }
    @Override public void close() {
        worker.shutdownNow();
        http.shutdownNow();
    }
}
