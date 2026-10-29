package dev.skydash.api;
import java.time.*;
import java.util.regex.*;
public record CacheEntry<T>(T value, Instant updated, Duration ttl) {
    public boolean expired(Instant now) {
        return !now.isBefore(updated.plus(ttl));
    }
    public String age(Instant now) {
        return "updated "+Math.max(0,Duration.between(updated,now).toMinutes())+" min ago";
    }
    public static Duration maxAge(String header,Duration fallback) {
        if(header==null)return fallback;
        if(header.toLowerCase(java.util.Locale.ROOT).matches(".*(no-store|no-cache).*"))return Duration.ZERO;
        Matcher m=Pattern.compile("(?:^|[,\\s])max-age=\"?(\\d+)",Pattern.CASE_INSENSITIVE).matcher(header);
        try {
            return m.find()?Duration.ofSeconds(Long.parseLong(m.group(1))):fallback;
        } catch(RuntimeException e) {
            return fallback;
        }
    }
}
