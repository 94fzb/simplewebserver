package com.hibegin.common.io.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface ReadWriteSelectorHandler {
    void handleWrite(ByteBuffer byteBuffer) throws IOException;

    ByteBuffer handleRead() throws IOException;

    void close();

    SocketChannel getChannel();

    boolean isPlain();
}
