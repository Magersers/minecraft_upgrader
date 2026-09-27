package dev.upgrade;

import dev.upgrade.core.CostEngine;
import java.util.*;

/** Independent utility anchors; recipe estimates never feed back into their own anchors. */
public final class PerformanceMath {
    private PerformanceMath() {}
    public static double wear(int damage,int maximum) {
        return maximum<=0?1:Math.max(.01,Math.min(1,1-damage/(double)maximum));
    }
    public static double weapon(double damage,double speed,double durability,double mining,double referenceCost) {
        double combat=Math.pow(Math.max(.01,damage*speed)/(7*1.6),1.6);
        double digging=mining<=0?0:Math.pow(mining/8,1.4);
        return Math.max(.01,referenceCost*Math.max(combat,digging)*Math.pow(Math.max(1,durability)/1561,.35));
    }
    public static double armor(double protection,double toughness,double durability,double referenceProtection,double referenceDurability,double referenceCost) {
        return Math.max(.01,referenceCost*Math.pow(Math.max(.1,protection)/referenceProtection,1.6)
                *Math.pow((2+Math.max(0,toughness))/4,.7)*Math.pow(Math.max(1,durability)/referenceDurability,.35));
    }
    /** Equal budgets per distinct unknown material; quantities divide each material's share. */
    public static Map<String,CostEngine.Value> reverse(Map<String,CostEngine.Value> anchors,Map<String,CostEngine.Value> known,
                                                     List<CostEngine.Route> routes,Set<String> denied) {
        Map<String,CostEngine.Value> estimates=new TreeMap<>(),frontier=new TreeMap<>(anchors);
        Set<String> seen=new HashSet<>(known.keySet()); seen.addAll(anchors.keySet());
        for (int depth=0;depth<16 && !frontier.isEmpty();depth++) {
            Map<String,CostEngine.Value> next=new TreeMap<>();
            for (var r:routes) {
                var output=frontier.get(r.output()); if (output==null) continue;
                double remaining=output.cost()*r.count()-r.overhead();
                Set<String> gates=new TreeSet<>(output.gates()); gates.addAll(r.gates());
                Map<String,Double> unknown=new TreeMap<>(); boolean safe=true;
                for (var input:r.inputs()) {
                    String chosen=CostEngine.cheapest(input,known);
                    if (chosen!=null) { var value=known.get(chosen); remaining-=value.cost()*input.count(); gates.addAll(value.gates()); }
                    else if (input.alternatives().size()==1) {
                        String id=input.alternatives().get(0);
                        if (seen.contains(id)||denied.contains(id)||id.equals(r.output())||id.startsWith("@")||id.contains("#")) { safe=false; break; }
                        unknown.merge(id,input.count(),Double::sum);
                    } else { safe=false; break; } // A tag does not establish which material was used.
                }
                if (!safe || unknown.isEmpty() || !Double.isFinite(remaining) || remaining<=0) continue;
                double share=remaining/unknown.size();
                for (var input:unknown.entrySet()) {
                    double price=share/input.getValue(); if (!Double.isFinite(price)||price<=0) continue;
                    var value=new CostEngine.Value(price,.85,String.format(Locale.ROOT,
                            "Обратная оценка из %s: бюджет %.2f E, видов неизвестных материалов %d, количество %.3f. Приблизительное распределение по рецепту %s",
                            r.output(),remaining,unknown.size(),input.getValue(),r.id()),gates);
                    next.merge(input.getKey(),value,(a,b)->a.cost()>=b.cost()?a:b);
                }
            }
            estimates.putAll(next); seen.addAll(next.keySet()); frontier=next;
        }
        return estimates;
    }
}
