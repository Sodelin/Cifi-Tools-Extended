package com.cifi.assistant;

/** Until a complete field contract is verified, every projection is partial. */
public final class CoverageNotice {
    private CoverageNotice() {}
    public static boolean partial(Iterable<String> reasons) {
        // Missing, malformed, unknown and even empty lists cannot establish
        // complete game-to-simulator coverage. Never display untrusted values.
        return true;
    }
}
