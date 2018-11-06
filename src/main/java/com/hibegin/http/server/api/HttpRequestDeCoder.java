package com.hibegin.http.server.api;

import java.nio.ByteBuffer;
import java.util.Map;

public interface HttpRequestDeCoder {

    Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception;

    HttpRequest getRequest();
}
