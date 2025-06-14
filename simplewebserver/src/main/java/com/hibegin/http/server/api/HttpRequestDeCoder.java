package com.hibegin.http.server.api;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;

import java.nio.ByteBuffer;
import java.util.Map;

public interface HttpRequestDeCoder {

    ReadWriteSelectorHandler getHandler();

    Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception;

    HttpRequest getRequest();

    void doNext();

}
