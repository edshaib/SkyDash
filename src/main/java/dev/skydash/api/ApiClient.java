package dev.skydash.api;

import com.google.gson.*;
import dev.skydash.Config;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sole HTTP owner. The client never accepts or transmits a Hypixel application key. */
public final class ApiClient implements AutoCloseable {
    public record ApiResponse(int statusCode, HttpHeaders headers, String body) {}
    @FunctionalInterface public interface Transport {
        ApiResponse send(HttpRequest request) throws Exception;
    }
    public static final List<String> PUBLIC=List.of(
        "skyblock/bazaar", "skyblock/firesales", "skyblock/news",
        "resources/skyblock/items", "resources/skyblock/collections",
        "resources/skyblock/election", "resources/skyblock/skills", "resources/skyblock/bingo");
    private static final List<String> PROFILE_ENDPOINTS=List.of(
        "skyblock/profile", "skyblock/garden", "skyblock/museum", "skyblock/bingo");
    private static final String SERVICE_UNAVAILABLE="Profile sync unavailable: SkyDash service is not configured";
    private record Settings(String backend, String requestedProfile, Duration ttl, Set<String> enabled) {}

    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r, "SkyDash-API");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Transport transport;
    private final Clock clock;
    private final RequestBudget budget;
    private final ConcurrentMap<String,CacheEntry<JsonObject>> cache=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Instant> retries=new ConcurrentHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean profilePending=new AtomicBoolean();
    private volatile Instant blockedUntil=Instant.EPOCH;
    private Instant manualAt=Instant.MIN;
    private volatile boolean authStopped;
    private volatile String status="Ready", uuid="", profile="", backend="";
    private int privateTurn;

    public ApiClient() { this(Clock.systemUTC()); }
    public ApiClient(Path ledger) { this(Clock.systemUTC(), null, ledger); }
    public ApiClient(Clock clock) { this(clock, null); }
    public ApiClient(Clock clock, Transport transport) { this(clock, transport, null); }
    public ApiClient(Clock clock, Transport transport, Path ledger) {
        this.clock=clock;
        budget=new RequestBudget(clock, ledger);
        this.transport=transport==null ? request -> {
            var response=http.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.headers(), response.body());
        } : transport;
    }
    /** Used by tests only; the game never waits on network work. */
    public CompletionStage<Void> idle() { return CompletableFuture.runAsync(() -> {}, worker); }
    public String status() {
        if (backend.isEmpty()) return SERVICE_UNAVAILABLE;
        if (authStopped) return "Profile service authorization rejected; contact the service operator";
        return status;
    }
    public CacheEntry<JsonObject> cached(String endpoint) { return cache.get(endpoint); }
    public String age(String endpoint) {
        var entry=cache.get(endpoint);
        return entry==null ? "unavailable" : entry.age(clock.instant());
    }
    public synchronized void identity(String playerUuid) {
        String next=playerUuid.replace("-", "");
        if (!uuid.equals(next)) {
            uuid=next;
            profile="";
            clearPrivate();
        }
    }
    private void clearPrivate() {
        cache.keySet().removeIf(endpoint -> !PUBLIC.contains(endpoint));
        retries.keySet().removeIf(endpoint -> !PUBLIC.contains(endpoint));
    }
    private synchronized Settings settings(Config config) {
        String next=config.backendUrl==null ? "" : config.backendUrl.trim();
        if (!next.equals(backend)) {
            backend=next;
            profile="";
            authStopped=false;
            clearPrivate();
        }
        Set<String> enabled=new HashSet<>();
        for (String id:List.of("garden", "skills", "museum", "collections", "bingo")) {
            if (config.module(id).enabled) enabled.add(id);
        }
        String requested=config.profileId==null ? "" : config.profileId.replace("-", "").trim();
        return new Settings(next, requested, Duration.ofSeconds(config.profileTtl()), Set.copyOf(enabled));
    }
    /** Tick only refreshes public data. Private cache expiry never triggers a request. */
    public void tick(Config config) {
        settings(config);
        refreshPublic(Duration.ofSeconds(config.publicTtl()));
    }
    private void refreshPublic(Duration ttl) {
        for (String endpoint:PUBLIC) {
            var entry=cache.get(endpoint);
            if (entry!=null && !entry.expired(clock.instant())) continue;
            if (!pending.add(endpoint)) continue;
            worker.execute(() -> {
                try { fetch(endpoint, "", false, "", "", ttl, false); }
                finally { pending.remove(endpoint); }
            });
        }
    }
    /** An explicit dashboard opening or Refresh action starts one bounded request batch. */
    public boolean requestProfile(Config config) { return manualRefresh(config); }
    public synchronized boolean manualRefresh(Config config) {
        Settings snapshot=settings(config);
        Instant now=clock.instant();
        if (now.isBefore(manualAt.plusSeconds(30))) {
            status="Manual refresh cooldown: 30 seconds";
            return false;
        }
        if (profilePending.get()) return false;
        manualAt=now;
        refreshPublic(Duration.ofSeconds(config.publicTtl()));
        if (snapshot.backend().isEmpty() || snapshot.enabled().isEmpty() || uuid.isEmpty() || authStopped) return true;
        if (!profilePending.compareAndSet(false, true)) return false;
        String owner=uuid;
        worker.execute(() -> {
            try { refreshOwn(snapshot, owner); }
            finally { profilePending.set(false); }
        });
        return true;
    }
    private boolean current(Settings settings, String owner) {
        return backend.equals(settings.backend()) && uuid.equals(owner);
    }
    private void refreshOwn(Settings settings, String owner) {
        if (!current(settings, owner)) return;
        // Discovery validates membership and seeds the same profile cache used by every module.
        boolean discovered=false;
        if (profile.isEmpty() || (!settings.requestedProfile().isEmpty() && !profile.equals(settings.requestedProfile()))) {
            if (!fetch("skyblock/profiles", "uuid="+owner, true, settings.backend(), owner, settings.ttl(), false)) return;
            if (!current(settings, owner)) return;
            var discovery=cache.get("skyblock/profiles");
            JsonObject selected=null;
            for (JsonElement element:Json.arr(discovery.value(), "profiles")) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate=element.getAsJsonObject();
                String id=Json.str(candidate, "profile_id", "").replace("-", "");
                if (!id.matches("[a-fA-F0-9]{32}") || !Json.obj(candidate, "members").has(owner)) continue;
                if (!settings.requestedProfile().isEmpty() ? settings.requestedProfile().equals(id)
                    : selected==null || Json.bool(candidate, "selected")) selected=candidate;
            }
            if (selected==null) {
                status="Own profile unavailable; check the selected profile";
                return;
            }
            profile=Json.str(selected, "profile_id", "").replace("-", "");
            cache.keySet().removeIf(endpoint -> PROFILE_ENDPOINTS.contains(endpoint));
            JsonObject shared=new JsonObject();
            shared.add("profile", selected.deepCopy());
            cache.put("skyblock/profile", new CacheEntry<>(shared, discovery.updated(), settings.ttl()));
            discovered=true;
        }
        int start=privateTurn;
        for (int i=0;i<PROFILE_ENDPOINTS.size();i++) {
            if (!current(settings, owner) || authStopped || clock.instant().isBefore(blockedUntil)) return;
            int turn=(start+i)%PROFILE_ENDPOINTS.size();
            String endpoint=PROFILE_ENDPOINTS.get(turn);
            String module=endpoint.substring(endpoint.lastIndexOf('/')+1);
            if (module.equals("profile")) {
                if (discovered || settings.enabled().stream().noneMatch(id -> !id.equals("bingo"))) continue;
            } else if (!settings.enabled().contains(module)) continue;
            String query=module.equals("bingo") ? "uuid="+owner : "profile="+profile;
            fetch(endpoint, query, true, settings.backend(), owner, settings.ttl(), false);
        }
    }
    private boolean fetch(String endpoint, String query, boolean privateRequest, String service,
                          String owner, Duration ttl, boolean force) {
        Instant now=clock.instant();
        if (privateRequest && (!owner.equals(uuid) || !service.equals(backend) || authStopped)) return false;
        var old=cache.get(endpoint);
        if (!force && old!=null && !old.expired(now)) return true;
        if (now.isBefore(blockedUntil) || now.isBefore(retries.getOrDefault(endpoint, Instant.EPOCH))) return false;
        try {
            URI base=URI.create(privateRequest ? service : "https://api.hypixel.net/v2");
            String host=base.getHost();
            if (!"https".equalsIgnoreCase(base.getScheme()) || host==null || base.getUserInfo()!=null
                || base.getQuery()!=null || base.getFragment()!=null
                || (privateRequest && host.equalsIgnoreCase("api.hypixel.net"))) {
                status="Profile service unavailable: invalid service configuration";
                return false;
            }
            String path=base.toString().replaceAll("/+$", "")+"/"+endpoint+(query.isEmpty()?"":"?"+query);
            // No application key exists in client configuration, URLs, or headers.
            var request=HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SkyDash/0.1.1").GET().build();
            if (privateRequest && !budget.acquire()) {
                status="Profile refresh budget used; showing cached data";
                return false;
            }
            if (privateRequest) {
                int turn=PROFILE_ENDPOINTS.indexOf(endpoint);
                if (turn>=0) privateTurn=(turn+1)%PROFILE_ENDPOINTS.size();
            }
            ApiResponse response=transport.send(request);
            Instant finished=clock.instant();
            if (privateRequest && (!owner.equals(uuid) || !service.equals(backend))) return false;
            int code=response.statusCode();
            if (code==401 || code==403) {
                if (privateRequest) authStopped=true;
                status="Service authorization rejected";
                retries.put(endpoint, finished.plusSeconds(300));
                return false;
            }
            if (code==429) {
                blockedUntil=finished.plusSeconds(resetSeconds(response.headers()));
                status="Service rate limited; showing cached data";
                return false;
            }
            if (code!=200) throw new IllegalStateException();
            JsonObject data=JsonParser.parseString(response.body()).getAsJsonObject();
            if (data.has("success") && !Json.bool(data, "success")) throw new IllegalStateException();
            Duration lifetime=privateRequest ? ttl
                : CacheEntry.maxAge(response.headers().firstValue("Cache-Control").orElse(null), ttl);
            if (!privateRequest) {
                long age=0;
                try { age=Long.parseLong(response.headers().firstValue("Age").orElse("0")); }
                catch (NumberFormatException ignored) {}
                lifetime=Duration.ofSeconds(Math.max(0, lifetime.toSeconds()-Math.max(0, age)));
            }
            if (privateRequest && response.headers().firstValue("RateLimit-Remaining").orElse("1").equals("0")) {
                blockedUntil=finished.plusSeconds(resetSeconds(response.headers()));
            }
            cache.put(endpoint, new CacheEntry<>(data, finished, lifetime));
            retries.put(endpoint, finished.plusSeconds(privateRequest ? 1 : Math.max(30, ttl.toSeconds())));
            status="Ready";
            return true;
        } catch (Exception ignored) {
            status="Service unavailable; showing cached data";
            retries.put(endpoint, clock.instant().plusSeconds(60));
            return false;
        }
    }
    private long resetSeconds(HttpHeaders headers) {
        try { return Math.max(1, Long.parseLong(headers.firstValue("RateLimit-Reset").orElse("60"))); }
        catch (NumberFormatException ignored) { return 60; }
    }
    public Optional<ProfileSnapshot> ownProfile() {
        var entry=cache.get("skyblock/profile");
        if (entry==null) return Optional.empty();
        try { return Optional.of(ProfileSnapshot.parse(entry.value(), uuid)); }
        catch (RuntimeException ignored) { return Optional.empty(); }
    }
    public String ownUuid() { return uuid; }
    public String ownProfileId() { return profile; }
    @Override public void close() { worker.shutdownNow(); http.shutdownNow(); }
}
