package com.hibegin.http.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ChunkedOutputStream extends FilterOutputStream {
    static final byte[] CRLF = "\r\n".getBytes();
    static final byte[] LAST_TOKEN = "0\r\n\r\n".getBytes();
    boolean eos = false;

    private ChunkedOutputStream() {
        super(null);
    }

    public ChunkedOutputStream(OutputStream os) {
        super(os);
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) return;
        out.write((Integer.toHexString(len)).getBytes());
        out.write(CRLF);
        out.write(b, off, len);
        out.write(CRLF);
    }

    public void eos() throws IOException {
        synchronized (this) {
            if (eos) return;
            eos = true;
        }
        out.write(LAST_TOKEN);
        out.flush();
    }

    public void close() throws IOException {
        eos();
        out.close();
    }
}