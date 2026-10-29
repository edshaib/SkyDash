package dev.skydash.api;
import com.google.gson.*;
import java.time.*;
import java.util.*;
public final class CoreTests {
    static int checks;
    static void check(boolean b) {
        checks++;
        if (!b) throw new AssertionError("Check " + checks);
    }
    static JsonObject fixture(String name) throws Exception {
        try(var in=CoreTests.class.getResourceAsStream("/"+name+".json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static void main(String[] args) throws Exception {
        var t=new MutableClock();
        var b=new RequestBudget(t);
        check(b.acquire());
        check(b.acquire());
        check(b.acquire());
        check(!b.acquire());
        t.ms=100_000;
        check(!b.acquire());
        t.ms=299_999;
        check(!b.acquire());
        t.ms=300_000;
        check(b.acquire());
        // A gradual refill must never allow a fourth request inside a rolling five minutes.
        var c=new MutableClock();
        var r=new RequestBudget(c);
        check(r.acquire());
        c.ms=100_000;
        check(r.acquire());
        c.ms=200_000;
        check(r.acquire());
        c.ms=299_999;
        check(!r.acquire());
        c.ms=300_000;
        check(r.acquire());
        check(!r.acquire());
        var parallel=new RequestBudget(new MutableClock());
        var accepted=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CompletableFuture.allOf(java.util.stream.IntStream.range(0,64).mapToObj(i->java.util.concurrent.CompletableFuture.runAsync(()-> {
            if(parallel.acquire())accepted.incrementAndGet();
        })).toArray(java.util.concurrent.CompletableFuture[]::new)).join();
        check(accepted.get()==3);
        var cache=new CacheEntry<>("value", Instant.EPOCH, Duration.ofSeconds(120));
        check(!cache.expired(Instant.EPOCH.plusSeconds(119)));
        check(cache.expired(Instant.EPOCH.plusSeconds(120)));
        check(cache.value().equals("value"));
        check(CacheEntry.maxAge("public, max-age=600",Duration.ofSeconds(30)).equals(Duration.ofSeconds(600)));
        check(CacheEntry.maxAge("no-store",Duration.ofSeconds(30)).isZero());
        check(CacheEntry.maxAge("max-age=oops",Duration.ofSeconds(30)).equals(Duration.ofSeconds(30)));
        var profile=ProfileSnapshot.parse(fixture("profile"),"abc");
        check(profile.skills().get("SKILL_FARMING")==1250);
        check(profile.collections().get("WHEAT")==999);
        check(profile.purse()==1234);
        check(profile.sacks().get("WHEAT")==12);
        check(ProfileSnapshot.parse(fixture("profile-disabled"),"abc").skills().isEmpty());
        boolean rejected=false;
        try {
            ProfileSnapshot.parse(fixture("profile-foreign"),"abc");
        } catch(IllegalArgumentException e) {
            rejected=true;
        }
        check(rejected);
        var ledger=java.nio.file.Files.createTempDirectory("skydash-budget-test").resolve("budget.txt");
        var persistentClock=new MutableClock();
        try {
            var persistent=new RequestBudget(persistentClock,ledger);
            check(persistent.acquire());
            check(persistent.acquire());
            check(persistent.acquire());
            var restarted=new RequestBudget(persistentClock,ledger);
            check(!restarted.acquire());
            persistentClock.ms=300000;
            check(restarted.acquire());
        } finally {
            java.nio.file.Files.deleteIfExists(ledger);
            java.nio.file.Files.deleteIfExists(ledger.getParent());
        }
        System.out.println("SkyDash: "+checks+" core checks passed");
        TransportTests.run();
    }
    static final class MutableClock extends Clock {
        long ms;
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        public Clock withZone(ZoneId z) {
            return this;
        }
        public Instant instant() {
            return Instant.ofEpochMilli(ms);
        }
    }
}
