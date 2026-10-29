package dev.skydash.api;
import java.time.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
/** Token bucket plus rolling guard: never over three starts in any five minutes. */
public final class RequestBudget {
    private final Clock clock;
    private final Path ledger;
    private final Deque<Instant> starts=new ArrayDeque<>();
    private Instant refill;
    private double tokens=3;
    private boolean storageHealthy=true;
    public RequestBudget(Clock clock) {
        this(clock,null);
    }
    public RequestBudget(Clock clock,Path ledger) {
        this.clock=clock;
        this.ledger=ledger;
        refill=clock.instant();
        if(ledger!=null&&Files.exists(ledger))try {
            for(String line:Files.readAllLines(ledger)) {
                Instant start=Instant.ofEpochMilli(Long.parseLong(line));
                if(start.plusSeconds(300).isAfter(refill))starts.addLast(start);
            }
            tokens=Math.max(0,3-starts.size());
        } catch(Exception e) {
            storageHealthy=false;
        }
    }
    /** Called on the API worker when persistence is enabled, never on tick/render. */
    public synchronized boolean acquire() {
        if(!storageHealthy)return false;
        Instant now=clock.instant();
        tokens=Math.min(3,tokens+Math.max(0,Duration.between(refill,now).toMillis())/100000.0);
        refill=now;
        while(!starts.isEmpty()&&!starts.peekFirst().plusSeconds(300).isAfter(now))starts.removeFirst();
        if(tokens<1||starts.size()>=3)return false;
        tokens-=1;
        starts.addLast(now);
        if(ledger!=null)try {
            Files.createDirectories(ledger.getParent());
            Path temp=ledger.resolveSibling(ledger.getFileName()+".tmp");
            Files.write(temp,starts.stream().map(i->Long.toString(i.toEpochMilli())).toList());
            try {
                Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException e) {
                Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(IOException e) {
            storageHealthy=false;
            return false;
        }
        return true;
    }
}
