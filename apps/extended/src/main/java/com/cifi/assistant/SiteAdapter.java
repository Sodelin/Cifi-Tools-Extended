package com.cifi.assistant;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** UI-thread-only site operations; does not authorize, back up, or complete imports. */
public final class SiteAdapter {
    public interface Result { void done(JSONObject value); }
    // One random marker per Android process; changes when the process restarts.
    private static final String PROCESS_ID=java.util.UUID.randomUUID().toString();
    private final WebView view;
    private final String scripts;
    public SiteAdapter(Context context,WebView view) throws Exception {
        this.view=view;
        try(java.io.InputStream a=context.getAssets().open("projection.js");java.io.InputStream b=context.getAssets().open("site-transaction.js")) {
            scripts="const module={exports:{}};\n"+new String(a.readAllBytes(),StandardCharsets.UTF_8)+"\nconst project=module.exports.project;module.exports={};\n"+new String(b.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private boolean trusted() {
        String url=view.getUrl();if(url==null)return false;Uri parsed=Uri.parse(url);
        return CifiOrigin.trusted(CifiOrigin.httpsUri(parsed.toString()));
    }
    private String prefix() {
        return "(()=>{if(location.origin!=='https://cifi-tools.com')throw Error('origin');"+
            "const p=document.querySelector('#app')?.__vue_app__?._context?.provides;"+
            "const pinia=p&&Reflect.ownKeys(p).map(k=>p[k]).find(v=>v&&v._s&&typeof v._s.get==='function');"+
            "const h=pinia?._s.get('hunter'),g=pinia?._s.get('gemPlanner');if(!h||!g)throw Error('stores');"+
            scripts+";const tx=module.exports.createSiteTransaction({origin:()=>location.origin,stores:()=>({hunter:h,gems:g}),storage:localStorage,project,uuid:()=>crypto.randomUUID()});";
    }
    private void run(String expression,Result callback) {
        if(!trusted()){callback.done(null);return;}
        view.evaluateJavascript(expression,encoded->{
            if(!trusted() || encoded==null){callback.done(null);return;}
            try {String text=new JSONArray("["+encoded+"]").getString(0);callback.done(new JSONObject(text));}
            catch(Exception ignored){callback.done(null);}
        });
    }
    public void capture(Result callback) {
        run(prefix()+"return JSON.stringify(tx.capture());})()", value->{
            try { if(value!=null)value.put("appProcessId",PROCESS_ID); }
            catch(Exception ignored) { value=null; }
            callback.done(value);
        });
    }
    public void apply(JSONObject before,JSONObject mapped,Result callback) {
        if(before==null||mapped==null){callback.done(null);return;}
        String expected=JSONObject.quote(before.toString()),incoming=JSONObject.quote(mapped.toString());
        run(prefix()+"return JSON.stringify(tx.apply(JSON.parse("+expected+"),JSON.parse("+incoming+")));})()",callback);
    }
}
