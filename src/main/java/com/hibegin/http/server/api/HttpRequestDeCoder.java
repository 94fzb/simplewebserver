package com.hibegin.http.server.api;

import com.hibegin.http.server.handler.ReadWriteSelectorHandler;

import java.nio.ByteBuffer;
import java.util.Map;

public interface HttpRequestDeCoder {

    ReadWriteSelectorHandler getHandler();

    Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception;

    HttpRequest getRequest();

    void doNext();

}
