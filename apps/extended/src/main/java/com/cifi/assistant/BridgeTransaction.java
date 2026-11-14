package com.cifi.assistant;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.json.JSONException;
import org.json.JSONObject;

/** One authenticated, memory-only import exchange. Call on a worker thread. */
public final class BridgeTransaction {
    private BridgeTransaction() {}
    public interface PreparedHandler {
        /** Return refusal before any write, or readback-passed only after durable verification. */
        String apply(JSONObject projection, String saveStamp) throws Exception;
    }
    public static String run(InputStream in, OutputStream out, JSONObject snapshot, PreparedHandler handler) throws IOException {
        try {
            if (snapshot == null || handler == null || !"https://cifi-tools.com".equals(snapshot.optString("origin"))
                    || snapshot.optJSONObject("hunter") == null || snapshot.optJSONObject("gems") == null)
                throw new IOException("Invalid site snapshot");
            JSONObject request=new JSONObject().put("operation","prepare-import").put("snapshot",snapshot);
            FrameCodec.write(out,request.toString());
            JSONObject prepared=new JSONObject(FrameCodec.read(in));
            String digest=prepared.optString("projectionSha256","");
            JSONObject projection=prepared.optJSONObject("projection");
            String stamp=prepared.optString("saveStamp","");
            if (!"prepared".equals(prepared.optString("status")) || !digest.matches("[0-9a-f]{64}") || projection == null || stamp.isEmpty())
                throw new IOException("Invalid prepared import");
            final String status;
            try { status=handler.apply(projection,stamp); }
            catch (Exception error) { throw new IOException("Import outcome unknown",error); }
            if (!"readback-passed".equals(status) && !"refused-before-write".equals(status))
                throw new IOException("Import outcome unknown");
            FrameCodec.write(out,new JSONObject().put("status",status).put("projectionSha256",digest).toString());
            JSONObject reply=new JSONObject(FrameCodec.read(in));
            if (!"closed".equals(reply.optString("status")) || !status.equals(reply.optString("outcome")))
                throw new IOException("Import close unconfirmed");
            return status;
        } catch (JSONException error) { throw new IOException("Invalid import response",error); }
    }
}
