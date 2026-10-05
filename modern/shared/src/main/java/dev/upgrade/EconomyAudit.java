package dev.upgrade;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.nio.file.*;
import java.util.*;

/** Separates unknown prices from items which have a price but cannot be staked as a plain stack. */
public final class EconomyAudit {
    private EconomyAudit() {}
    public static Path write() throws java.io.IOException {
        JsonObject root=new JsonObject(),mods=new JsonObject(); JsonArray items=new JsonArray();
        Map<String,int[]> counts=new TreeMap<>();
        for (var item:BuiltInRegistries.ITEM) {
            String id=BuiltInRegistries.ITEM.getKey(item).toString(); String mod=id.split(":",2)[0];
            var value=Economy.current.values().get(id); var row=new JsonObject(); row.addProperty("id",id);
            String status=Economy.current.denied().contains(id)||!Economy.plain(item.getDefaultInstance())?"excluded":value==null?"unknown":"valued";
            int[] count=counts.computeIfAbsent(mod,k->new int[3]); count[status.equals("valued")?0:status.equals("unknown")?1:2]++;
            row.addProperty("status",status);
            if (value!=null) { row.addProperty("cost",value.cost()); row.addProperty("pristine_stake_cost",Math.min(value.cost(),Math.min(PerformancePricing.stakeLimits.getOrDefault(id,value.cost()),PricingPolicy.hard && PerformancePricing.simple.contains(id)?PricingPolicy.simpleCap:Double.POSITIVE_INFINITY))); row.addProperty("confidence",value.confidence()); row.add("gates",new Gson().toJsonTree(value.gates())); }
            row.addProperty("reason",Economy.current.explanations().getOrDefault(id,"")); items.add(row);
        }
        counts.forEach((mod,count)-> { JsonObject row=new JsonObject(); row.addProperty("valued",count[0]); row.addProperty("unknown",count[1]); row.addProperty("excluded",count[2]); mods.add(mod,row); });
        root.addProperty("hard_mode",PricingPolicy.hard); root.addProperty("simple_stake_cap",PricingPolicy.simpleCap);
        root.add("mods",mods); root.add("items",items);
        Path path=Path.of("logs","upgrade-economy.json"); Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(root)); return path;
    }
}
