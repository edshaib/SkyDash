package dev.skydash;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import java.util.*;
import java.util.regex.*;
/** Local observations; purse delta is net change and is not counted again from coin chat. */
public final class SessionTracker {
    private static final Pattern PURSE=Pattern.compile("(?:Purse|Piggy|Coins):?\\s*([\\d,]+(?:\\.\\d+)?)",Pattern.CASE_INSENSITIVE);
    private static final Pattern XP=Pattern.compile("\\+([\\d,.]+)\\s+(?:([A-Za-z]+)\\s+)?(?:XP|Experience)",Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM=Pattern.compile("(?:You received|You found|You collected|RARE DROP!)\\s+(.+?)(?:\\s+x([\\d,]+))?[!.]?$",Pattern.CASE_INSENSITIVE);
    public long started=System.currentTimeMillis();
    public double coins,xp;
    public final Map<String,Double> skillXp=new TreeMap<>();
    public final Map<String,Long> items=new TreeMap<>();
    private Double firstPurse;
    private double coinBase;
    private String lastAction="";
    private boolean active;
    public boolean active() {
        return active;
    }
    public double hours() {
        return Math.max(1.0/3600,(System.currentTimeMillis()-started)/3600000.0);
    }
    public void rebasePurse() {
        coinBase=coins;
        firstPurse=null;
    }
    public void reset() {
        started=System.currentTimeMillis();
        coins=0;
        xp=0;
        firstPurse=null;
        coinBase=0;
        items.clear();
        skillXp.clear();
        lastAction="";
    }
    public void message(String text,boolean actionBar) {
        if(!active)return;
        text=text.replaceAll("§.","");
        if(actionBar&&text.equals(lastAction))return;
        if(actionBar)lastAction=text;
        Matcher x=XP.matcher(text);
        while(x.find()) {
            try {
                double n=Double.parseDouble(x.group(1).replace(",",""));
                xp+=n;
                skillXp.merge(x.group(2)==null?"OTHER":x.group(2).toUpperCase(Locale.ROOT),n,Double::sum);
            } catch(NumberFormatException ignored) {
            }
        }
        if(!actionBar) {
            Matcher m=ITEM.matcher(text);
            if(m.find())items.merge(m.group(1),m.group(2)==null?1:Long.parseLong(m.group(2).replace(",","")),Long::sum);
        }
    }
    public void tick() {
        var mc=Minecraft.getInstance();
        active=false;
        if(mc.level==null||mc.getCurrentServer()==null)return;
        String ip=mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];
        if(!(ip.equals("hypixel.net")||ip.endsWith(".hypixel.net")))return;
        var board=mc.level.getScoreboard();
        var obj=board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if(obj==null||!obj.getDisplayName().getString().contains("SKYBLOCK"))return;
        active=true;
        for(var entry:board.listPlayerScores(obj)) {
            String line=PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()),entry.ownerName()).getString().replaceAll("§.","");
            Matcher m=PURSE.matcher(line);
            if(m.find())try {
                double n=Double.parseDouble(m.group(1).replace(",",""));
                if(firstPurse==null)firstPurse=n;
                coins=coinBase+n-firstPurse;
            } catch(NumberFormatException ignored) {
            }
        }
    }
}
