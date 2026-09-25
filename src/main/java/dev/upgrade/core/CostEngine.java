package dev.upgrade.core;
import java.util.*;
public final class CostEngine {
    public static final double DEFAULT_EFFICIENCY = 0.85;
    public record Value(double cost,double confidence,String source,Set<String> gates) {
        public Value {if(!Double.isFinite(cost)||cost<=0||!Double.isFinite(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("Invalid value");gates=Set.copyOf(gates);}
    }
    public record Input(List<String> alternatives,double count) {
        public Input {alternatives=List.copyOf(alternatives);if(alternatives.isEmpty()||!Double.isFinite(count)||count<=0)throw new IllegalArgumentException("Invalid ingredient");}
    }
    public record Route(String id,String output,double count,List<Input> inputs,double overhead,double confidence,Set<String> gates) {
        public Route {inputs=List.copyOf(inputs);gates=Set.copyOf(gates);if(!Double.isFinite(count)||count<=0||!Double.isFinite(overhead)||overhead<0||!Double.isFinite(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("Invalid route");}
    }
    public record Result(Map<String,Value> values,Set<String> quarantined,int passes) {}
    public static Result solve(Map<String,Value> seeds,List<Route> routes,int maxPasses) {
        if(maxPasses<1)throw new IllegalArgumentException("passes");Map<String,Value> values=new TreeMap<>(seeds);Set<String> changing=new HashSet<>();int passes=0;
        for(;passes<maxPasses;passes++) {
            Map<String,Value> next=new TreeMap<>(values);changing.clear();
            for(Route route:routes) {
                double total=route.overhead,confidence=route.confidence;Set<String> gates=new TreeSet<>(route.gates);boolean known=true;
                for(Input input:route.inputs) {
                    Value cheapest=null;
                    for(String id:input.alternatives) {Value candidate=values.get(id);if(candidate==null){known=false;break;}if(cheapest==null||candidate.cost<cheapest.cost)cheapest=candidate;}
                    if(!known)break;total+=cheapest.cost*input.count;confidence=Math.min(confidence,cheapest.confidence);gates.addAll(cheapest.gates);
                }
                if(!known)continue;double cost=total/route.count;if(!Double.isFinite(cost)||cost<=0)continue;Value old=next.get(route.output);
                if(old==null||cost<old.cost*(1-1e-10)){next.put(route.output,new Value(cost,confidence,route.id,gates));changing.add(route.output);}
            }
            values=next;if(changing.isEmpty())return new Result(Map.copyOf(values),Set.of(),passes+1);
        }
        Set<String> bad=new HashSet<>(changing);boolean expanded;
        do{expanded=false;for(Route route:routes)if(route.inputs.stream().anyMatch(i->i.alternatives.stream().anyMatch(bad::contains)))expanded|=bad.add(route.output);}while(expanded);
        bad.forEach(values::remove);return new Result(Map.copyOf(values),Set.copyOf(bad),passes);
    }
    public static double encounter(double search,double combat,double consumables,double deathChance,double deathLoss,double setup,double horizon,double success,double dropChance,double meanCount) {
        for(double x:new double[]{search,combat,consumables,deathLoss,setup})if(!Double.isFinite(x)||x<0)throw new IllegalArgumentException("negative effort");
        if(!Double.isFinite(deathChance)||deathChance<0||deathChance>1||!Double.isFinite(horizon)||horizon<=0||!Double.isFinite(success)||success<=0||success>1||!Double.isFinite(dropChance)||dropChance<=0||dropChance>1||!Double.isFinite(meanCount)||meanCount<=0)throw new IllegalArgumentException("probability");
        double value=(search+combat+consumables+deathChance*deathLoss+setup/horizon)/(success*dropChance*meanCount);
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("zero/overflow effort");return value;
    }
    public static double chance(double stake,double target,double efficiency) {
        if(!Double.isFinite(stake)||!Double.isFinite(target)||stake<=0||target<=stake||!Double.isFinite(efficiency)||efficiency<=0||efficiency>1)throw new IllegalArgumentException("Not an upgrade");
        return Math.min(0.95,efficiency*stake/target);
    }
}
