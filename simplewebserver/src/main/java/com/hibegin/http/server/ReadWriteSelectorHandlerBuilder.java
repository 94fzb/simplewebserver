package com.hibegin.http.server;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public interface ReadWriteSelectorHandlerBuilder {

    ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel) throws IOException;
}
