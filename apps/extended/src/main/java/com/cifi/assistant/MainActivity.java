package com.cifi.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

/** App-window presentation of CIFI Tools with a user-triggered local refresh. */
public final class MainActivity extends Activity {
    private static final int REQUEST_TERMUX=711;
    private WebView site;private Button sync;private SiteAdapter adapter;private int pendingAction;String lastStatus="idle";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(0xff111827);getWindow().setNavigationBarColor(0xff111827);
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)WebView.setWebContentsDebuggingEnabled(true);
        site=new WebView(this);WebSettings settings=site.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        site.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String host=request.getUrl().getHost();return !"https".equalsIgnoreCase(request.getUrl().getScheme())||!"cifi-tools.com".equalsIgnoreCase(host);}});
        FrameLayout root=new FrameLayout(this);root.addView(site,new FrameLayout.LayoutParams(-1,-1));
        sync=new Button(this);sync.setText("Sync");sync.setAllCaps(false);sync.setTextColor(Color.WHITE);sync.setContentDescription("Sync CIFI game data");
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff5136a3);bg.setCornerRadius(32*getResources().getDisplayMetrics().density);sync.setBackground(bg);
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams((int)(80*getResources().getDisplayMetrics().density),(int)(48*getResources().getDisplayMetrics().density),Gravity.RIGHT|Gravity.BOTTOM);int margin=(int)(16*getResources().getDisplayMetrics().density);pos.setMargins(margin,margin,margin,margin);root.addView(sync,pos);
        sync.setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,sync);menu.getMenu().add(0,1,0,"Refresh from CIFI");menu.getMenu().add(0,2,1,"Finish previous refresh");menu.getMenu().add(0,3,2,"Sync signed-in website");menu.getMenu().add(0,4,3,"What gets imported?");menu.setOnMenuItemClickListener(item->{if(item.getItemId()==4)showImportInfo();else requestAction(item.getItemId());return true;});menu.show();});
        setContentView(root);try{adapter=new SiteAdapter(this,site);}catch(Exception ignored){adapter=null;}
        if(state==null||site.restoreState(state)==null)site.loadUrl("https://cifi-tools.com/borge");
    }
    private void showImportInfo(){
        new AlertDialog.Builder(this).setTitle("Import coverage").setMessage("Refresh reads your latest saved CIFI file, which may be older than your current game session. It updates supported hunter stats, upgrades, gems, and one phone-scan build for each hunter; existing builds are kept. Coverage is partial: some inputs, including individual trinkets and ship rank and crew totals, are not imported. Check important values before simulating.\n\nRefresh and verification are temporarily unavailable while recovery is being repaired. Website sync is temporarily unavailable while its browser privacy and recovery checks are being completed. Refresh verification is also temporarily unavailable; do not repeat a pending refresh.").setPositiveButton("OK",null).show();
    }
    private void requestAction(int action){
        if(action==3){message("Website sync is temporarily unavailable while privacy and recovery checks are completed.");return;}
        if(action==1){message("Refresh is temporarily unavailable while recovery is being repaired.");return;}
        if(action==2){message("Refresh verification is temporarily unavailable. Keep any recovery record and do not retry.");return;}
        if(adapter==null){message("CIFI Tools is unavailable.");return;}
        if(checkSelfPermission(TermuxLauncher.PERMISSION)!=PackageManager.PERMISSION_GRANTED){pendingAction=action;requestPermissions(new String[]{TermuxLauncher.PERMISSION},REQUEST_TERMUX);return;}
        runAction(action);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==REQUEST_TERMUX){int action=pendingAction;pendingAction=0;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&action!=0)runAction(action);else message("Termux permission is needed to read your game save.");}}
    private void runAction(int action){
        sync.setEnabled(false);
        if(action==3){
            lastStatus="reading";message("Syncing with your open Firefox session…");
            try{TermuxLauncher.startWebsiteSync(this,(ok,result)->{lastStatus=ok?result:"unknown";sync.setEnabled(true);message(ok?("already-synced".equals(result)?"Supported website fields already synced. Check import coverage for missing inputs.":"Supported website fields and cloud synced. Check import coverage for missing inputs."):"Website sync was not confirmed. Open the signed-in CIFI Tools tab in Firefox and check any pending recovery before retrying.");});}
            catch(Exception e){lastStatus="unknown";sync.setEnabled(true);message("Website sync could not start.");}
        }else if(action==1){new RefreshController(cb->adapter.capture(cb),(before,mapped,cb)->adapter.apply(before,mapped,cb),(cap,cb)->TermuxLauncher.startVerified(this,cap,cb),this::refreshStatus).start();}
        else{new RecoveryController(adapter,(cap,cb)->TermuxLauncher.startRecovery(this,cap,cb),this::recoveryStatus).start();}
    }
    private void refreshStatus(String value){
        lastStatus=value;
        if("reading".equals(value))return;sync.setEnabled(true);
        if("pending-restart-readback".equals(value)||"pending-restart-readback-partial".equals(value)){
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Refresh needs verification").setMessage(("pending-restart-readback-partial".equals(value)?"Some inputs were unavailable; check your build before simulating.\n\n":"")+"Please wait a few seconds for local storage, then close and reopen the app and choose Finish previous refresh.").setPositiveButton("Close app",(d,w)->{finishAndRemoveTask();android.os.Process.killProcess(android.os.Process.myPid());}).setNegativeButton("Later",null).create();
            dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!isFinishing()&&dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);},12000);
        }else if("reconciliation-required".equals(value))message("A previous refresh needs reconciliation. Do not retry.");
        else if("refused-before-write".equals(value))message("The site changed during refresh. Restart the app and finish the previous refresh before trying again.");
        else message("Refresh could not be confirmed. Restart the app and choose Finish previous refresh before trying again.");
    }
    private void recoveryStatus(String value){lastStatus=value;if("reading".equals(value))return;sync.setEnabled(true);message("reconciled".equals(value)?"Supported refreshed fields verified. Other inputs may still need checking.":("not-applied".equals(value)?"No changes were applied. You can try refreshing again.":"Refresh could not be verified. The recovery record was kept when available."));}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onSaveInstanceState(Bundle state){site.saveState(state);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(site.canGoBack())site.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){site.destroy();super.onDestroy();}
}
