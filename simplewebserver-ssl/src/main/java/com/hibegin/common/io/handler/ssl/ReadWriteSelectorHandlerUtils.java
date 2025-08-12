package com.hibegin.common.io.handler.ssl;

import com.hibegin.common.io.handler.PlainReadWriteSelectorHandler;
import com.hibegin.common.io.handler.ReadWriteSelectorHandler;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class ReadWriteSelectorHandlerUtils {

    public static ReadWriteSelectorHandler buildReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize) {
        return new PlainReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
    }

    public static ReadWriteSelectorHandler buildServerReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SSLContext sslContext,
                                                                               boolean disablePlainRead) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(socketChannel, sslContext, maxRequestBufferSize, false, disablePlainRead);
    }

    public static ReadWriteSelectorHandler buildClientReadWriteSelectorHandler(SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SSLContext sslContext,
                                                                               String host,
                                                                               int port,
                                                                               boolean sendSNI) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(socketChannel, sslContext, maxRequestBufferSize, true, false, host, port, sendSNI);
    }
}
