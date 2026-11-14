package com.cifi.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** One-transaction, memory-only app endpoint. Not exposed until controller arms it. */
public final class TransferProvider extends ContentProvider {
    public interface Listener { void received(byte[] payload); void unknown(); }
    private static final Object LOCK=new Object();
    private static ExchangeGate active;
    private static Listener listener;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    /** Must be called by the in-process UI only, after its user-triggered snapshot. */
    public static String arm(byte[] snapshot,Listener callback) {
        if(callback==null) throw new IllegalArgumentException("missing callback");
        synchronized(LOCK) {
            if(active!=null) throw new IllegalStateException("exchange already active");
            active=new ExchangeGate(snapshot,TimeUnit.SECONDS.toNanos(60));listener=callback;
            return active.capability();
        }
    }
    /** Disarms this in-memory exchange, never clears the encrypted recovery anchor. */
    public static void disarm(Listener owner) {
        synchronized(LOCK) {
            if(owner!=null && listener==owner) {active=null;listener=null;}
        }
    }
    /** Test instrumentation cleanup; not called by application flows. */
    static void resetForTest() { synchronized(LOCK) { active=null;listener=null; } }
    private static boolean current(ExchangeGate gate,Listener target) {
        synchronized(LOCK) { return active==gate && listener==target; }
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        final ExchangeGate gate; final Listener target;
        synchronized(LOCK) { gate=active;target=listener; }
        if(gate==null || target==null || uri.getPathSegments().size()!=1) throw new SecurityException("no active exchange");
        final String cap=uri.getPathSegments().get(0); final int uid=Binder.getCallingUid();
        try {
            if("r".equals(mode)) {
                byte[] snapshot=gate.takeSnapshot(uid,cap);
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    FrameCodec.write(out,new String(snapshot,StandardCharsets.UTF_8));
                } catch(Exception ignored) { MAIN.post(()->{if(current(gate,target))target.unknown();}); }}).start();
                return pipe[0];
            }
            if("w".equals(mode)) {
                gate.beginResponse(uid,cap);
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{
                    try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                        String response=FrameCodec.read(in);
                        if(in.read()!=-1) throw new java.io.IOException("trailing bytes");
                        gate.finishResponse(response.getBytes(StandardCharsets.UTF_8));
                        byte[] payload=gate.takeResponse();MAIN.post(()->{if(current(gate,target))target.received(payload);});
                    } catch(Exception error) {gate.failResponse();MAIN.post(()->{if(current(gate,target))target.unknown();});}
                }).start();
                return pipe[1];
            }
            throw new SecurityException("invalid mode");
        } catch(SecurityException|IllegalStateException error) { throw error; }
        catch(Exception error) { if(gate.state()==ExchangeGate.State.WRITE_TAKEN)gate.failResponse();throw new FileNotFoundException("exchange unavailable"); }
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
}
