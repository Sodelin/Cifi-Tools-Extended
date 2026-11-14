package com.cifi.assistant;

import android.app.Activity;

/** Public snapshot: phone-specific integration is intentionally unavailable. */
public final class TermuxLauncher {
    public static final String PERMISSION="com.termux.permission.RUN_COMMAND";
    public interface Result { void finished(boolean success); }
    public interface VerifiedResult { void finished(boolean success,String digest); }
    public interface WebsiteResult { void finished(boolean success,String result); }
    private TermuxLauncher() {}
    public static void startVerified(Activity a,String capability,VerifiedResult callback) { throw new UnsupportedOperationException("Not configured"); }
    public static void startRecovery(Activity a,String capability,Result callback) { throw new UnsupportedOperationException("Not configured"); }
    public static void startWebsiteSync(Activity a,WebsiteResult callback) { throw new UnsupportedOperationException("Not configured"); }
}
