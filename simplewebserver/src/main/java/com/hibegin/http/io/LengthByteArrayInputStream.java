package com.hibegin.http.io;

import java.io.ByteArrayInputStream;

public class LengthByteArrayInputStream extends ByteArrayInputStream {
    private final long length;

    public LengthByteArrayInputStream(byte[] buf) {
        super(buf);
        this.length = buf.length;
    }

    public long getLength() {
        return length;
    }
}
