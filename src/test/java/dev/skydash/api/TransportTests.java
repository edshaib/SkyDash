package dev.skydash.api;

import dev.skydash.Config;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class TransportTests {
    private static final String UUID="11111111111111111111111111111111";
    private static final String PROFILE="22222222222222222222222222222222";
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Transport check " + checks);
    }
    private static final class Fake implements ApiClient.Transport {
        final List<HttpRequest> calls=new CopyOnWriteArrayList<>();
        int privateCode=200;
        public ApiClient.ApiResponse send(HttpRequest request) {
            calls.add(request);
            boolean privateRequest=request.uri().getHost().equals("service.invalid");
            String json="{\"success\":true}";
            if (request.uri().getPath().endsWith("profiles")) {
                json="{\"success\":true,\"profiles\":[{\"profile_id\":\""+PROFILE+"\",\"selected\":true,\"members\":{\""+UUID+"\":{}}}]}";
            }
            return new ApiClient.ApiResponse(privateRequest?privateCode:200,
                HttpHeaders.of(Map.of("Cache-Control",List.of("max-age=600"),"RateLimit-Reset",List.of("120")),(k,v)->true),
                privateRequest&&privateCode!=200?"private service response body":json);
        }
        long privateCalls() {
            return calls.stream().filter(r->r.uri().getHost().equals("service.invalid")).count();
        }
    }
    private static void idle(ApiClient client)throws Exception {
        client.idle().toCompletableFuture().get(5,TimeUnit.SECONDS);
    }
    public static int run()throws Exception {
        var clock=new CoreTests.MutableClock();
        clock.ms=1_000_000;
        var config=new Config();
        var fake=new Fake();
        try(var client=new ApiClient(clock,fake)) {
            client.identity(UUID);
            client.tick(config);idle(client);
            check(fake.calls.size()==ApiClient.PUBLIC.size());
            check(fake.privateCalls()==0);
            check(client.status().contains("not configured"));
            check(client.manualRefresh(config));idle(client);
            check(fake.privateCalls()==0);
        }
        config.backendUrl="https://service.invalid/v2";
        fake=new Fake();
        try(var client=new ApiClient(clock,fake)) {
            client.identity(UUID);
            for(int i=0;i<10;i++){client.tick(config);idle(client);}
            check(fake.privateCalls()==0); // No recurring profile polling, even with a backend configured.
            check(client.requestProfile(config));idle(client);
            check(fake.privateCalls()==3);
            check(client.ownProfile().isPresent());
            check(fake.calls.stream().noneMatch(r->r.headers().firstValue("API-Key").isPresent()));
            check(fake.calls.stream().noneMatch(r->r.headers().firstValue("Authorization").isPresent()));
            check(fake.calls.stream().allMatch(r->r.headers().firstValue("User-Agent").orElse("").equals("SkyDash/0.1.1")));
            int count=fake.calls.size();
            check(!client.manualRefresh(config));idle(client);
            check(fake.calls.size()==count);
            clock.ms+=600000;
            for(int i=0;i<10;i++){client.tick(config);idle(client);}
            check(fake.privateCalls()==3); // Expiry / budget refill never initiates a private request.
            check(client.manualRefresh(config));idle(client);
            check(fake.privateCalls()<=6);
            check(fake.calls.stream().anyMatch(r->r.uri().getPath().endsWith("bingo")));
        }
        for(int code:new int[]{401,403}) {
            fake=new Fake();fake.privateCode=code;
            try(var client=new ApiClient(clock,fake)) {
                client.identity(UUID);client.manualRefresh(config);idle(client);
                check(fake.privateCalls()==1);
                clock.ms+=600000;client.manualRefresh(config);idle(client);
                check(fake.privateCalls()==1);
                check(client.status().contains("authorization rejected"));
            }
        }
        fake=new Fake();fake.privateCode=429;
        try(var client=new ApiClient(clock,fake)) {
            client.identity(UUID);client.manualRefresh(config);idle(client);
            check(fake.privateCalls()==1);
            clock.ms+=119999;client.manualRefresh(config);idle(client);
            check(fake.privateCalls()==1);
            clock.ms+=30000;client.manualRefresh(config);idle(client);
            check(fake.privateCalls()==2);
        }
        for(String invalid:List.of("https://api.hypixel.net/v2","http://service.invalid/v2","https://user:pass@service.invalid/v2")) {
            fake=new Fake();config.backendUrl=invalid;
            try(var client=new ApiClient(clock,fake)) {
                client.identity(UUID);client.manualRefresh(config);idle(client);
                check(fake.privateCalls()==0);
                check(fake.calls.size()==ApiClient.PUBLIC.size());
            }
        }
        Path directory=Files.createTempDirectory("skydash-config-migration");
        Path file=directory.resolve("skydash.json");
        try {
            Files.writeString(file,"{\"apiKey\":\"legacy-fixture-secret\",\"proxyUrl\":\"https://old.invalid\"}");
            var migrated=Config.load(file);migrated.save(file);
            check(!Files.readString(file).contains("legacy-fixture-secret"));
            check(!Files.readString(file).contains("apiKey"));
            check(migrated.backendUrl.isEmpty());
            check(Arrays.stream(Config.class.getFields()).noneMatch(f->f.getName().equals("apiKey")));
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(directory);}
        System.out.println("SkyDash: "+checks+" transport checks passed");
        return checks;
    }
}
