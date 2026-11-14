package com.cifi.assistant;

import android.net.Credentials;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.IOException;
import org.json.JSONObject;

/** A single memory-only exchange. Run off the Android main thread. */
public final class LocalBridge {
    private LocalBridge() {}

    /**
     * The expected Termux UID must come from PackageManager for the trusted,
     * locally verified Termux installation, never from the service payload.
     * A fresh caller-generated nonce is a rendezvous name, not an identity.
     */
    public static String exchange(String nonce, int expectedTermuxUid, String request) throws IOException {
        if (nonce == null || !nonce.matches("[0-9a-f]{32}")) throw new IOException("Invalid rendezvous");
        if (expectedTermuxUid < 0) throw new IOException("Invalid service identity");
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress("cifi-native-" + nonce, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(15000);
            Credentials peer = socket.getPeerCredentials();
            if (peer == null || peer.getUid() != expectedTermuxUid) throw new IOException("Untrusted local peer");
            FrameCodec.write(socket.getOutputStream(),request);
            return FrameCodec.read(socket.getInputStream());
        }
    }
    /** Transaction with recovery semantics; handler must establish durable readback. */
    public static String transact(String nonce, int expectedTermuxUid, JSONObject snapshot,
                                  BridgeTransaction.PreparedHandler handler) throws IOException {
        if (nonce == null || !nonce.matches("[0-9a-f]{32}") || expectedTermuxUid < 0)
            throw new IOException("Invalid rendezvous or service identity");
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress("cifi-native-" + nonce, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(60000);
            Credentials peer=socket.getPeerCredentials();
            if (peer == null || peer.getUid() != expectedTermuxUid) throw new IOException("Untrusted local peer");
            return BridgeTransaction.run(socket.getInputStream(),socket.getOutputStream(),snapshot,handler);
        }
    }

}
