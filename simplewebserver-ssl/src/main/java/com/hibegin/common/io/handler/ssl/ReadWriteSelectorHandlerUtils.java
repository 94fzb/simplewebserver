package com.hibegin.common.io.handler.ssl;

import com.hibegin.common.io.handler.PlainReadWriteSelectorHandler;
import com.hibegin.common.io.handler.ReadWriteSelectorHandler;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class ReadWriteSelectorHandlerUtils {

    public static ReadWriteSelectorHandler buildReadWriteSelectorHandler(Selector selector, SocketChannel socketChannel, int maxRequestBufferSize) {
        return new PlainReadWriteSelectorHandler(selector, socketChannel, maxRequestBufferSize);
    }

    public static ReadWriteSelectorHandler buildServerReadWriteSelectorHandler(Selector selector,
                                                                               SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SSLContext sslContext,
                                                                               boolean disablePlainRead) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(selector, socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(selector, socketChannel, sslContext, maxRequestBufferSize, false, disablePlainRead);
    }

    public static ReadWriteSelectorHandler buildClientReadWriteSelectorHandler(Selector selector,
                                                                               SocketChannel socketChannel, int maxRequestBufferSize,
                                                                               SSLContext sslContext,
                                                                               String host,
                                                                               int port,
                                                                               boolean sendSNI) throws IOException {
        if (Objects.isNull(sslContext)) {
            return buildReadWriteSelectorHandler(selector, socketChannel, maxRequestBufferSize);
        }
        return new SslReadWriteSelectorHandler(selector, socketChannel, sslContext, maxRequestBufferSize, true, false, host, port, sendSNI);
    }
}
