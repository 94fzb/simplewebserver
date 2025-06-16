package com.hibegin.http.io;

import java.io.ByteArrayInputStream;

public class LengthByteArrayInputStream extends ByteArrayInputStream implements KnownLengthStream {
    private final long length;

    public LengthByteArrayInputStream(byte[] buf) {
        super(buf);
        this.length = buf.length;
    }

    @Override
    public long getLength() {
        return length;
    }
}
