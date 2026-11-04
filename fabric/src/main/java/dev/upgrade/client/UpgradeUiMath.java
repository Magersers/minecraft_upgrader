package dev.upgrade.client;

/** Pure presentation math: never changes server probabilities or outcomes. */
public final class UpgradeUiMath {
    private UpgradeUiMath() {}
    public static double normalize(double angle) {
        if (!Double.isFinite(angle)) return 0;
        double turn=Math.PI*2;
        return ((angle%turn)+turn)%turn;
    }
    public static double angleAt(double x,double y) { return normalize(Math.atan2(y,x)+Math.PI/2); }
    public static boolean onTrack(double x,double y,double radius) {
        double distance=Math.hypot(x,y);
        return distance>=radius-9 && distance<=radius+14;
    }
    /** Nearest feasible stack for a desired value ratio; zero means no legal upgrade. */
    public static int nearestCount(double stake,double multiplier,double unit,int limit) {
        if (!Double.isFinite(stake)||stake<=0||!Double.isFinite(unit)||unit<=0||
                !Double.isFinite(multiplier)||multiplier<=1||limit<1) return 0;
        int minimum=(int)Math.min(Integer.MAX_VALUE,Math.floor(stake/unit)+1);
        if (minimum>limit) return 0;
        long desired=Math.round(stake*multiplier/unit);
        return (int)Math.max(minimum,Math.min(limit,desired));
    }
    public static double distance(double stake,double multiplier,double reward) {
        return Math.abs(Math.log(reward/(stake*multiplier)));
    }
}
