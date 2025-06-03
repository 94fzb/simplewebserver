package com.hibegin.common.io.handler;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class ReadWriteSelectorHandlerUtils {

    public static ReadWriteSelectorHandler buildReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize) {
        return new PlainReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
    }

    public static ReadWriteSelectorHandler buildServerReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SelectionKey selectionKey,
                                                                               SSLContext sslContext,
                                                                               boolean disablePlainRead) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(socketChannel, selectionKey, sslContext, maxRequestBufferSize, false, disablePlainRead);
    }

    public static ReadWriteSelectorHandler buildClientReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SelectionKey selectionKey,
                                                                               SSLContext sslContext,
                                                                               String host,
                                                                               int port,
                                                                               boolean sendSNI) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(socketChannel, selectionKey, sslContext, maxRequestBufferSize, true, false, host, port, sendSNI);
    }
}
