package com.cifi.tools;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/** Exact streaming comparison. Neither input is copied to persistent storage. */
final class BackupCompare {
    private BackupCompare() {}
    // Refuse counts outside the InputStream contract before buffering can
    // mistake an invalid refill for EOF or accept an impossible length.
    private static final class CheckedRead extends FilterInputStream {
        CheckedRead(InputStream input) { super(input); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count=in.read(bytes,offset,length);
            if(count < -1 || count > length || (length>0 && count==0)) throw new IOException("Invalid provider read count");
            return count;
        }
    }
    static boolean identical(InputStream first, InputStream second) throws IOException {
        if (first == null || second == null || first == second) throw new IOException("Independent files unavailable");
        BufferedInputStream a = new BufferedInputStream(new CheckedRead(first));
        BufferedInputStream b = new BufferedInputStream(new CheckedRead(second));
        for (;;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Comparison interrupted");
            int x = a.read();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Comparison interrupted");
            int y = b.read();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Comparison interrupted");
            if (x != y) return false;
            if (x == -1) return true;
        }
    }
}
