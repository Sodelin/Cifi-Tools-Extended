package com.cifi.tools;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Browser-owned session: this app never receives site credentials or cookies. */
public final class MainActivity extends Activity {
    private static final int FIRST_FILE=1, SECOND_FILE=2;
    private Uri firstFile;
    private Button compare;
    private final AtomicBoolean comparisonCancelled = new AtomicBoolean();
    private Thread comparisonWorker;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff111827);
        getWindow().setNavigationBarColor(0xff111827);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);root.setPadding(32,32,32,32);root.setBackgroundColor(0xff111827);
        TextView title=new TextView(this);title.setText("CIFI Tools");title.setTextSize(26);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView hint=new TextView(this);hint.setText("Use the browser where you signed in to CIFI Tools.");hint.setTextColor(0xffd1d5db);hint.setGravity(Gravity.CENTER);hint.setPadding(0,18,0,24);root.addView(hint);
        Button open=new Button(this);open.setText("Open CIFI Tools");open.setAllCaps(false);open.setOnClickListener(v->openTools(false));root.addView(open);
        Button chooseBrowser=new Button(this);chooseBrowser.setText("Choose browser");chooseBrowser.setAllCaps(false);chooseBrowser.setOnClickListener(v->openTools(true));root.addView(chooseBrowser);
        Button settings=new Button(this);settings.setText("Website settings and backups");settings.setAllCaps(false);settings.setOnClickListener(v->openSite("https://cifi-tools.com/settings",true));root.addView(settings);
        compare=new Button(this);compare.setText("Compare two backup files");compare.setAllCaps(false);
        compare.setOnClickListener(v->{firstFile=null;chooseFile(FIRST_FILE);});root.addView(compare);setContentView(root);
        if(state==null)openTools(false);
    }
    private void openTools(boolean chooseBrowser){
        openSite("https://cifi-tools.com/borge",chooseBrowser);
    }
    private void openSite(String address,boolean chooseBrowser){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(address));
        // Standard Custom Tabs protocol, using the user's chosen browser and its session.
        Bundle extras=new Bundle();extras.putBinder("android.support.customtabs.extra.SESSION",null);intent.putExtras(extras);
        try{startActivity(chooseBrowser ? Intent.createChooser(intent,"Choose your browser") : intent);}catch(ActivityNotFoundException e){new android.app.AlertDialog.Builder(this).setMessage("Install or enable a browser to open CIFI Tools.").setPositiveButton("OK",null).show();}
    }
    private void chooseFile(int request) {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(picker,request); }
        catch (ActivityNotFoundException e) { firstFile=null;Toast.makeText(this,"No file picker is available.",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=FIRST_FILE && request!=SECOND_FILE)return;
        if(result!=RESULT_OK || data==null || data.getData()==null) {firstFile=null;return;}
        if(request==FIRST_FILE) {firstFile=data.getData();chooseFile(SECOND_FILE);return;}
        Uri a=firstFile,b=data.getData();firstFile=null;
        if(a==null){Toast.makeText(this,"The first selection expired. Choose both files again.",Toast.LENGTH_LONG).show();return;}
        compare.setEnabled(false);compare.setText("Comparing…");
        comparisonCancelled.set(false);
        comparisonWorker=new Thread(()->{
          String message=null;
          try {
            if(comparisonCancelled.get()) return;
            try(InputStream left=getContentResolver().openInputStream(a)) {
                if(comparisonCancelled.get()) return;
                try(InputStream right=getContentResolver().openInputStream(b)) {
                    if(comparisonCancelled.get()) return;
                    message=BackupCompare.identical(left,right)
                        ? "The bytes read matched. The files may change during or after this check. This does not verify backup validity or your cloud account."
                        : "The bytes read differed. This does not identify changed data, verify backup validity, or tell which file is newer.";
                }
            } catch(Exception e) {message="Could not read both files. No data was changed.";}
          } finally {
            final String resultMessage=message;
            runOnUiThread(()->{
                if(!isFinishing() && !isDestroyed()) {
                    comparisonWorker=null;compare.setEnabled(true);compare.setText("Compare two backup files");
                    if(!comparisonCancelled.get() && resultMessage!=null) new android.app.AlertDialog.Builder(this)
                        .setTitle("Backup comparison").setMessage(resultMessage).setPositiveButton("OK",null).show();
                }
            });
          }
        },"backup-compare");
        comparisonWorker.start();
    }
    @Override protected void onStop() {
        if(comparisonWorker!=null) {
            comparisonCancelled.set(true);
            compare.setText("Stopping comparison…");
            comparisonWorker.interrupt();
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        comparisonCancelled.set(true);
        if(comparisonWorker!=null) comparisonWorker.interrupt();
        super.onDestroy();
    }
}
