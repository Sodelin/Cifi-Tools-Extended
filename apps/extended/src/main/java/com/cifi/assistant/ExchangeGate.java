package com.cifi.assistant;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.LongSupplier;

/** Memory-only, one-use authorization for a future provider exchange.
 * No provider, service, filesystem or permission is created by this class.
 * The owner must independently retain encrypted recovery before any mutation.
 */
public final class ExchangeGate {
    public static final int MAX_BYTES=1024*1024;
    public static final int SHELL_UID=2000;
    public enum State { READY, READ_TAKEN, WRITE_TAKEN, RECEIVED, UNKNOWN, EXPIRED }
    private final byte[] secret;
    private final LongSupplier clock;
    private final long start, lifetime;
    private byte[] snapshot, received;
    private State state=State.READY;

    public ExchangeGate(byte[] snapshot,long lifetimeNanos) {
        this(snapshot,lifetimeNanos,System::nanoTime,new SecureRandom());
    }
    ExchangeGate(byte[] snapshot,long lifetimeNanos,LongSupplier clock,SecureRandom random) {
        if(snapshot==null || snapshot.length==0 || snapshot.length>MAX_BYTES || lifetimeNanos<=0 || clock==null || random==null)
            throw new IllegalArgumentException("Invalid exchange");
        this.snapshot=snapshot.clone();this.lifetime=lifetimeNanos;this.clock=clock;this.start=clock.getAsLong();
        secret=new byte[32];random.nextBytes(secret);
    }
    /** Caller must keep this capability out of logs and persistent records. */
    public synchronized String capability() {
        if(state!=State.READY) throw new IllegalStateException("Exchange already used");
        StringBuilder out=new StringBuilder(64);
        for(byte b:secret) out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static int hex(char c) {
        if(c>='0' && c<='9') return c-'0';
        if(c>='a' && c<='f') return c-'a'+10;
        return -1;
    }
    private boolean authentic(int uid,String capability) {
        if(uid!=SHELL_UID || capability==null || capability.length()!=64) return false;
        byte[] candidate=new byte[32];
        for(int i=0;i<32;i++) {
            int a=hex(capability.charAt(i*2)), b=hex(capability.charAt(i*2+1));
            if(a<0||b<0) return false;
            candidate[i]=(byte)((a<<4)|b);
        }
        return MessageDigest.isEqual(secret,candidate);
    }
    private void check(int uid,String capability,State expected) {
        if(!authentic(uid,capability)) throw new SecurityException("Unauthorized exchange");
        if(clock.getAsLong()-start>=lifetime) { if(state!=State.RECEIVED && state!=State.UNKNOWN) { state=state==State.WRITE_TAKEN ? State.UNKNOWN : State.EXPIRED; eraseSnapshot(); } throw new IllegalStateException("Exchange expired"); }
        if(state!=expected) throw new IllegalStateException("Exchange already used or out of order");
    }
    private void eraseSnapshot() { if(snapshot!=null) Arrays.fill(snapshot,(byte)0);snapshot=null; }
    public synchronized byte[] takeSnapshot(int uid,String capability) {
        check(uid,capability,State.READY);byte[] value=snapshot.clone();eraseSnapshot();state=State.READ_TAKEN;return value;
    }
    /** Claims the single response stream before opening it; failure cannot be replayed. */
    public synchronized void beginResponse(int uid,String capability) {
        check(uid,capability,State.READ_TAKEN);state=State.WRITE_TAKEN;
    }
    /** Called only after the complete bounded response has been read into memory. */
    public synchronized void finishResponse(byte[] value) {
        if(state!=State.WRITE_TAKEN) throw new IllegalStateException("No response claimed");
        if(value==null || value.length==0 || value.length>MAX_BYTES || clock.getAsLong()-start>=lifetime) {
            state=State.UNKNOWN;throw new IllegalStateException("Invalid or expired response");
        }
        received=value.clone();state=State.RECEIVED;
    }
    public synchronized void failResponse() { if(state==State.WRITE_TAKEN) state=State.UNKNOWN; }
    public synchronized byte[] takeResponse() {
        if(state!=State.RECEIVED || received==null) throw new IllegalStateException("Response unavailable");
        byte[] result=received.clone();Arrays.fill(received,(byte)0);received=null;return result;
    }
    public synchronized State state() { return state; }
}
