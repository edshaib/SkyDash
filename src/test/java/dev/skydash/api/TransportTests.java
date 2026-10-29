package dev.skydash.api;
import dev.skydash.Config;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
public final class TransportTests {
    private static final String UUID="11111111111111111111111111111111", PROFILE="22222222222222222222222222222222";
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if(!condition)throw new AssertionError("Transport check "+checks);
    }
    private static final class Fake implements ApiClient.Transport {
        final List<HttpRequest> calls=new CopyOnWriteArrayList<>();
        int privateCode=200;
        public ApiClient.ApiResponse send(HttpRequest request) {
            calls.add(request);
            String path=request.uri().getPath();
            boolean auth=request.headers().firstValue("API-Key").isPresent();
            String json="{\"success\":true}";
            if(path.endsWith("profiles"))json="{\"success\":true,\"profiles\":[{\"profile_id\":\""+PROFILE+"\",\"selected\":true,\"members\":{\""+UUID+"\":{}}}]}";
            return new ApiClient.ApiResponse(auth?privateCode:200,HttpHeaders.of(Map.of("Cache-Control",List.of("max-age=600"),"RateLimit-Reset",List.of("120")),(k,v)->true),(auth&&privateCode!=200)?"secret error body must not be shown":json);
        }
        long privateCalls() {
            return calls.stream().filter(r->r.headers().firstValue("API-Key").isPresent()).count();
        }
    }
    private static void idle(ApiClient a)throws Exception {
        a.idle().toCompletableFuture().get(5,TimeUnit.SECONDS);
    }
    public static int run()throws Exception {
        var clock=new CoreTests.MutableClock();
        clock.ms=1_000_000;
        var fake=new Fake();
        var config=new Config();
        config.apiKey="fixture-only-key";
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==1);
            check(fake.calls.size()==ApiClient.PUBLIC.size()+1);
            check(fake.calls.stream().allMatch(r->r.headers().firstValue("User-Agent").orElse("").equals("SkyDash/0.1.0")));
            check(fake.calls.stream().noneMatch(r->r.uri().toString().contains(config.apiKey)));
            a.tick(config);
            idle(a);
            a.tick(config);
            idle(a);
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==3);
            check(a.ownProfile().isPresent());
            int count=fake.calls.size();
            check(a.manualRefresh(config));
            idle(a);
            check(!a.manualRefresh(config));
            check(fake.calls.size()==count);
            // fresh public caches remain fresh
            clock.ms+=300000;
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==4);
        }
        fake=new Fake();
        fake.privateCode=403;
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            a.tick(config);
            idle(a);
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==1);
            check(a.status().contains("authorization rejected"));
            clock.ms+=600000;
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==1);
        }
        fake=new Fake();
        fake.privateCode=429;
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            a.tick(config);
            idle(a);
            clock.ms+=119999;
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==1);
            clock.ms+=1;
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==2);
        }
        fake=new Fake();
        config.apiKey="";
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            a.tick(config);
            idle(a);
            check(fake.privateCalls()==0);
            check(fake.calls.size()==ApiClient.PUBLIC.size());
        }
        fake=new Fake();
        config.apiKey="fixture-only-key";
        config.proxyUrl="https://proxy.invalid/v2";
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            for(int i=0; i<5; i++) {
                a.tick(config);
                idle(a);
            }
            check(fake.calls.stream().noneMatch(r->r.headers().firstValue("API-Key").isPresent()));
            check(fake.calls.stream().filter(r->r.uri().getHost().equals("proxy.invalid")).count()==3);
            check(fake.calls.stream().filter(r->r.uri().getHost().equals("api.hypixel.net")).count()==ApiClient.PUBLIC.size());
        }
        // Budget denial must not advance round robin and starve the waiting endpoint.
        fake=new Fake();
        config.proxyUrl="";
        try(var a=new ApiClient(clock,fake)) {
            a.identity(UUID);
            for(int i=0; i<12; i++) {
                a.tick(config);
                idle(a);
            }
            check(fake.privateCalls()==3);
            clock.ms+=300000;
            a.tick(config);
            idle(a);
            check(fake.calls.getLast().uri().getPath().endsWith("bingo"));
        }
        System.out.println("SkyDash: "+checks+" transport checks passed");
        return checks;
    }
}
