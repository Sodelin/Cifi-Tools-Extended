package com.cifi.assistant;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Coordinates one user-triggered import. All callbacks must run on the UI thread.
 * Persistence and recovery are owned by the Termux runner, not this controller.
 */
public final class RefreshController {
    public interface Capture { void run(SiteAdapter.Result callback); }
    public interface Apply { void run(JSONObject before,JSONObject projection,SiteAdapter.Result callback); }
    public interface Launch { void run(String capability,TermuxLauncher.VerifiedResult callback) throws Exception; }
    public interface Status { void update(String status); }
    private final Capture capture; private final Apply apply; private final Launch launch; private final Status status;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable timeout; private long generation; private TransferProvider.Listener owner;
    private JSONObject before, response; private String commandDigest; private boolean busy, commandDone, commandOk, terminal, captureConsumed, applyStarted, mutationUnsettled;
    public RefreshController(Capture capture,Apply apply,Launch launch,Status status) {
        if(capture==null||apply==null||launch==null||status==null)throw new IllegalArgumentException("missing dependency");
        this.capture=capture;this.apply=apply;this.launch=launch;this.status=status;
    }
    private void end(String message) { if(terminal)return;terminal=true;busy=false;if(timeout!=null)handler.removeCallbacks(timeout);TransferProvider.disarm(owner);owner=null;before=null;response=null;status.update(message); }
    public void start() {
        if(busy) return;
        // A timeout or callback error cannot cancel an in-flight WebView write.
        // Only an external, durable reconciliation protocol can authorize reuse
        // across controllers/processes; this local latch is defense in depth.
        if(mutationUnsettled){status.update("reconciliation-required");return;}
        busy=true;terminal=false;captureConsumed=false;applyStarted=false;commandDone=false;commandOk=false;commandDigest=null;before=null;response=null;
        final long epoch=++generation;timeout=()->{if(epoch==generation)end("unknown");};handler.postDelayed(timeout,65000);status.update("reading");
        try {capture.run(value->{
            if(terminal||epoch!=generation)return;
            if(captureConsumed){end("unknown");return;}captureConsumed=true;
            if(value==null){end("unavailable");return;}
            before=value;
            try {
                owner=new TransferProvider.Listener(){
                    @Override public void received(byte[] bytes) {
                        if(terminal||epoch!=generation)return;
                        if(response!=null||applyStarted){end("unknown");return;}
                        try { response=new JSONObject(new String(bytes,StandardCharsets.UTF_8)); }
                        catch(Exception e){end("unknown");return;}
                        proceed(epoch);
                    }
                    @Override public void unknown(){if(epoch==generation)end("unknown");}
                };
                String cap=TransferProvider.arm(value.toString().getBytes(StandardCharsets.UTF_8),owner);
                launch.run(cap,(ok,digest)->{if(terminal||epoch!=generation)return;if(commandDone){end("unknown");return;}commandDone=true;commandOk=ok;commandDigest=digest;if(!ok){end("unknown");return;}proceed(epoch);});
            } catch(Exception e){end("unknown");}
        });} catch(Exception e){end("unavailable");}
    }
    private void proceed(long epoch) {
        if(terminal||epoch!=generation||!commandDone||!commandOk||response==null)return;
        JSONObject projection; boolean partial;
        String digest=response.optString("projectionSha256"),stamp=response.optString("saveStamp");
        try {
            if(!digest.matches("[0-9a-f]{64}")||!digest.equals(commandDigest)||stamp.isEmpty())throw new IllegalArgumentException();
            byte[] bytes=android.util.Base64.decode(response.getString("projectionBytes"),android.util.Base64.DEFAULT);
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder actual=new StringBuilder();
            for(byte b:hash)actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!java.security.MessageDigest.isEqual(actual.toString().getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII)))throw new SecurityException();
            projection=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            org.json.JSONArray reasons=projection.optJSONArray("unmapped");
            java.util.List<String> categories=new java.util.ArrayList<>();
            if(reasons!=null)for(int i=0;i<reasons.length();i++) {
                Object reason=reasons.opt(i);if(reason instanceof String)categories.add((String)reason);
            }
            partial=CoverageNotice.partial(categories);
        } catch(Exception e){end("unknown");return;}
        // Disarm before applying; no other response can be delivered.
        applyStarted=true;mutationUnsettled=true;TransferProvider.disarm(owner);owner=null;JSONObject snapshot=before;response=null;before=null;
        try {apply.run(snapshot,projection,result->{
            if(terminal||epoch!=generation)return;
            String value=result==null?"unknown":result.optString("status","unknown");
            if("pending-restart-readback".equals(value))end(partial?"pending-restart-readback-partial":"pending-restart-readback");
            else if("refused-before-write".equals(value))end("refused-before-write");
            else end("unknown");
        });} catch(Exception e){end("unknown");}
    }
}
