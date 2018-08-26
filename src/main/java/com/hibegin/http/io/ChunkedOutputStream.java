package com.hibegin.http.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ChunkedOutputStream extends FilterOutputStream {
    static final byte[] CRLF = "\r\n".getBytes();
    static final byte[] LAST_TOKEN = "0\r\n\r\n".getBytes();
    boolean eos = false;

    public ChunkedOutputStream(OutputStream os) {
        super(os);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }
        out.write((Integer.toHexString(len)).getBytes());
        out.write(CRLF);
        out.write(b, off, len);
        out.write(CRLF);
    }

    private void eos() throws IOException {
        synchronized (this) {
            if (eos) {
                return;
            }
            eos = true;
        }
        out.write(LAST_TOKEN);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        eos();
        out.close();
    }
}