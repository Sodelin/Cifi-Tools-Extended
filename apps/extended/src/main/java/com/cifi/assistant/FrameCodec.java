package com.cifi.assistant;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Length-prefixed UTF-8 transport matching native/protocol.py; no persistence. */
public final class FrameCodec {
    public static final int MAX_MESSAGE = 1024 * 1024;
    private FrameCodec() {}
    public static void write(OutputStream output, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_MESSAGE) throw new IOException("Invalid message length");
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(bytes.length);
        stream.write(bytes);
        stream.flush();
    }
    public static String read(InputStream input) throws IOException {
        DataInputStream stream = new DataInputStream(input);
        int length = stream.readInt();
        if (length <= 0 || length > MAX_MESSAGE) throw new IOException("Invalid message length");
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) { throw new IOException("Invalid UTF-8", error); }
    }
}
