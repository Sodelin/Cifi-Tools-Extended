package com.cifi.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BackupCompareTest {
    private static InputStream input(String value) { return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static InputStream chunks(byte[] bytes, int chunk) {
        return new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(len,chunk)); }
        };
    }
    public static void main(String[] args) throws Exception {
        if (!BackupCompare.identical(input("same"),input("same"))) throw new AssertionError("equal files");
        if (BackupCompare.identical(input("same"),input("same!"))) throw new AssertionError("different length");
        if (BackupCompare.identical(input("same"),input("sand"))) throw new AssertionError("different content");
        if (!BackupCompare.identical(input(""),input(""))) throw new AssertionError("empty files");
        InputStream shared=input("");
        try {BackupCompare.identical(shared,shared);throw new AssertionError("shared stream accepted");}
        catch (IOException expected) { }
        byte[] large=new byte[32771];for(int i=0;i<large.length;i++)large[i]=(byte)(i*31);
        if(!BackupCompare.identical(chunks(large,1),chunks(large,37)))throw new AssertionError("different chunking");
        byte[] altered=large.clone();altered[16384]^=1;
        if(BackupCompare.identical(chunks(large,11),chunks(altered,17)))throw new AssertionError("late difference");
        try { BackupCompare.identical(input("x"), new InputStream(){ public int read() throws IOException {throw new IOException();}}); throw new AssertionError("read failure"); }
        catch (IOException expected) { }
        for(boolean left:new boolean[]{true,false}) for(int mode:new int[]{0,-2,1}) {
            InputStream invalid=new InputStream(){public int read(){return 65;}public int read(byte[] b,int off,int len){return mode==1?len+1:mode;}};
            try {BackupCompare.identical(left?invalid:input(""),left?input(""):invalid);throw new AssertionError("invalid refill accepted");}
            catch (IOException expected) { }
        }
        try {
            BackupCompare.identical(input(""),new InputStream(){public int read(){Thread.currentThread().interrupt();return -1;}});
            throw new AssertionError("interrupt must not match");
        } catch (java.io.InterruptedIOException expected) { }
        finally {Thread.interrupted();}
        int[] reads={0};
        try {
            BackupCompare.identical(new InputStream(){public int read(){Thread.currentThread().interrupt();return -1;}},
                new InputStream(){public int read(){reads[0]++;return -1;}});
            throw new AssertionError("interrupt must not match");
        } catch (java.io.InterruptedIOException expected) {if(reads[0]!=0)throw new AssertionError("second provider read");}
        finally {Thread.interrupted();}
        System.out.println("BackupCompare synthetic checks passed");
    }
}
