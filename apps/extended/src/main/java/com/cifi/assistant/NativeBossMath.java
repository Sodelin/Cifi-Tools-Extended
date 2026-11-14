package com.cifi.assistant;

/** CIFI-independent boss-odds primitives; results depend on the supplied simulation. */
public final class NativeBossMath {
    static { System.loadLibrary("cifi_math"); }
    private NativeBossMath() {}
    /** field 0 = estimate, 1 = lower 95% bound, 2 = upper 95% bound. */
    public static native double estimate(long wins, long trials, int field);
    public static native double chance(double probability, long attempts);
    /** Propagate the 95% win-rate interval through independent attempts. */
    public static native double chanceWithin(long wins, long trials, long attempts, int field);
}
