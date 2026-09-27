package dev.upgrade.core;

/** Shared animation duration; the server also enforces a minimum before payout. */
public final class RollTiming {
    public static final int TICKS = 80;
    public static final long MILLIS = TICKS * 50L;
    public static boolean canSettle(long now, long due, boolean acknowledged) {
        // Recover an interrupted screen after 10 seconds; no reward is paid before due.
        return now >= due && (acknowledged || now - due >= 200);
    }
    public static double progress(long elapsedMillis) {
        return Math.max(0, Math.min(1, elapsedMillis / (double) MILLIS));
    }
    public static double turns(double progress, double roll) {
        double t = Math.max(0, Math.min(1, progress));
        return (5 + roll) * (1 - Math.pow(1 - t, 4));
    }
}
