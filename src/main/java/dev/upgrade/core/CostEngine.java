package dev.upgrade.core;

import java.util.*;

/** Minimum acquisition cost over an AND/OR recipe graph. Unknown alternatives are not requirements. */
public final class CostEngine {
    public static final double DEFAULT_EFFICIENCY = 0.85;
    public record Value(double cost, double confidence, String source, Set<String> gates) {
        public Value {
            if (!Double.isFinite(cost) || cost <= 0 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("Invalid value");
            gates = Set.copyOf(gates);
        }
    }
    public record Input(List<String> alternatives, double count) {
        public Input {
            alternatives = alternatives.stream().distinct().sorted().toList();
            if (alternatives.isEmpty() || !Double.isFinite(count) || count <= 0) throw new IllegalArgumentException("Invalid ingredient");
        }
    }
    public record Route(String id, String output, double count, List<Input> inputs, double overhead, double confidence, Set<String> gates) {
        public Route {
            inputs = List.copyOf(inputs); gates = Set.copyOf(gates);
            if (!Double.isFinite(count) || count <= 0 || !Double.isFinite(overhead) || overhead < 0
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("Invalid route");
        }
    }
    public record Part(String item, double count, double unitCost) {}
    public record Breakdown(String recipe, double outputCount, double overhead, List<Part> parts) {
        public Breakdown { parts = List.copyOf(parts); }
    }
    public record Result(Map<String, Value> values, Set<String> quarantined, int passes, Map<String, Breakdown> breakdowns) {}

    public static Result solve(Map<String, Value> seeds, List<Route> recipes, int minPasses) {
        return solve(seeds,recipes,minPasses,Map.of(),Map.of());
    }
    /** Optional economy bounds apply during every relaxation, including reversible storage recipes. */
    public static Result solve(Map<String, Value> seeds, List<Route> recipes, int minPasses,
                               Map<String,Double> floors,Map<String,Double> ceilings) {
        for (var map:List.of(floors,ceilings)) for (double n:map.values())
            if (!Double.isFinite(n)||n<=0) throw new IllegalArgumentException("Invalid price bound");
        for (String id:floors.keySet()) if (floors.get(id)>ceilings.getOrDefault(id,Double.POSITIVE_INFINITY))
            throw new IllegalArgumentException("Conflicting price bounds: "+id);
        if (minPasses < 1) throw new IllegalArgumentException("passes");
        List<Route> routes = recipes.stream().sorted(Comparator.comparing(Route::id)).toList();
        Set<String> vertices = new HashSet<>(seeds.keySet());
        for (Route route : routes) {
            vertices.add(route.output());
            route.inputs().forEach(i -> vertices.addAll(i.alternatives()));
        }
        // A simple path can be as deep as the graph. A fixed 128-pass cutoff falsely
        // classified long (but finite) modpack chains as money-generating cycles.
        int limit = Math.max(minPasses, vertices.size() + 1);
        Map<String, Value> values = new TreeMap<>();
        for (var entry:seeds.entrySet()) values.put(entry.getKey(),bounded(entry.getKey(),entry.getValue(),floors,ceilings));
        Map<String, Breakdown> breakdowns = new HashMap<>();
        Set<String> changing = new HashSet<>();
        int passes = 0;
        for (; passes < limit; passes++) {
            Map<String, Value> next = new TreeMap<>(values);
            changing.clear();
            for (Route route : routes) {
                double total = route.overhead(), confidence = route.confidence();
                Set<String> gates = new TreeSet<>(route.gates());
                List<Part> parts = new ArrayList<>();
                boolean known = true;
                for (Input input : route.inputs()) {
                    String chosen = cheapest(input, values);
                    if (chosen == null) { known = false; break; }
                    Value value = values.get(chosen);
                    total += value.cost() * input.count();
                    confidence = Math.min(confidence, value.confidence());
                    gates.addAll(value.gates());
                    parts.add(new Part(chosen, input.count(), value.cost()));
                }
                if (!known) continue;
                double cost = total / route.count();
                if (!Double.isFinite(cost) || cost <= 0) continue;
                Value candidate = bounded(route.output(),new Value(cost, confidence, route.id(), gates),floors,ceilings), old = next.get(route.output());
                if (better(candidate, old)) {
                    next.put(route.output(), candidate);
                    if (candidate.cost()==cost) breakdowns.put(route.output(), new Breakdown(route.id(), route.count(), route.overhead(), parts));
                    else breakdowns.remove(route.output());
                    changing.add(route.output());
                }
            }
            values = next;
            if (changing.isEmpty()) return result(values, Set.of(), passes + 1, breakdowns);
        }
        Map<String, Value> finalValues = values;
        Set<String> bad = new HashSet<>(changing);
        boolean expanded;
        do {
            expanded = false;
            for (Route route : routes) {
                // Only propagate through recipes whose other inputs are actually obtainable.
                boolean affected = route.inputs().stream().anyMatch(i -> i.alternatives().stream().anyMatch(bad::contains));
                boolean possible = true;
                for (Input input : route.inputs()) {
                    if (input.alternatives().stream().noneMatch(id -> bad.contains(id) || finalValues.containsKey(id))) {
                        possible = false; break;
                    }
                }
                if (affected && possible) expanded |= bad.add(route.output());
            }
        } while (expanded);
        bad.forEach(values::remove); bad.forEach(breakdowns::remove);
        return result(values, bad, passes, breakdowns);
    }
    private static Value bounded(String id,Value value,Map<String,Double> floors,Map<String,Double> ceilings) {
        double cost=Math.min(ceilings.getOrDefault(id,Double.POSITIVE_INFINITY),Math.max(floors.getOrDefault(id,0d),value.cost()));
        if (cost==value.cost()) return value;
        return new Value(cost,value.confidence(),String.format(Locale.ROOT,"%s баланса %.2f E (до ограничения %.2f E): %s",
                cost>value.cost()?"Минимум":"Предел",cost,value.cost(),value.source()),value.gates());
    }
    private static Result result(Map<String, Value> values, Set<String> bad, int passes, Map<String, Breakdown> breakdowns) {
        return new Result(Map.copyOf(values), Set.copyOf(bad), passes, Map.copyOf(breakdowns));
    }
    public static String cheapest(Input input, Map<String, Value> values) {
        String chosen = null;
        for (String id : input.alternatives()) {
            Value candidate = values.get(id);
            if (candidate != null && (chosen == null || better(candidate, values.get(chosen)))) chosen = id;
        }
        return chosen;
    }
    private static boolean better(Value candidate, Value old) {
        if (old == null || candidate.cost() < old.cost() * (1 - 1e-10)) return true;
        if (Math.abs(candidate.cost() - old.cost()) > old.cost() * 1e-10) return false;
        // Equal-cost paths should not remain locked just because a gated recipe was visited first.
        if (old.gates().containsAll(candidate.gates()) && !old.gates().equals(candidate.gates())) return true;
        return old.gates().equals(candidate.gates()) && candidate.confidence() > old.confidence();
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
