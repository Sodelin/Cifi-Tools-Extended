package com.cifi.assistant;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Reconcile a previously pending import after a real app process restart. */
public final class RecoveryController {
    public interface Launch { void run(String capability,TermuxLauncher.Result callback) throws Exception; }
    public interface Status { void update(String value); }
    private final SiteAdapter site;private final Launch launch;private final Status status;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeout=()->finish("unknown");
    private TransferProvider.Listener owner;private boolean terminal,busy,captureConsumed,commandDone,commandOk,responseDone;private String responseStatus;
    public RecoveryController(SiteAdapter site,Launch launch,Status status){this.site=site;this.launch=launch;this.status=status;}
    private void finish(String value){if(terminal)return;terminal=true;busy=false;handler.removeCallbacks(timeout);TransferProvider.disarm(owner);owner=null;status.update(value);}
    private void check(){if(commandDone&&responseDone)finish(commandOk&&("reconciled".equals(responseStatus)||"not-applied".equals(responseStatus))?responseStatus:"unknown");}
    public void start(){
        if(busy||terminal)return;busy=true;
        status.update("reading");handler.postDelayed(timeout,65000);
        site.capture(snapshot->{
            if(terminal)return;if(captureConsumed){finish("unknown");return;}captureConsumed=true;if(snapshot==null){finish("unavailable");return;}
            try{
                owner=new TransferProvider.Listener(){
                    public void received(byte[] value){
                        if(terminal)return;if(responseDone){finish("unknown");return;}try{responseStatus=new JSONObject(new String(value,StandardCharsets.UTF_8)).optString("status");}catch(Exception ignored){}
                        responseDone=true;check();
                    }
                    public void unknown(){finish("unknown");}
                };
                String cap=TransferProvider.arm(snapshot.toString().getBytes(StandardCharsets.UTF_8),owner);
                launch.run(cap,ok->{if(terminal)return;if(commandDone){finish("unknown");return;}commandDone=true;commandOk=ok;if(!ok){finish("unknown");return;}check();});
            }catch(Exception e){finish("unknown");}
        });
    }
}
