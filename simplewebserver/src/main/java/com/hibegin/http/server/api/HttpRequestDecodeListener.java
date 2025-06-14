package com.hibegin.http.server.api;

public interface HttpRequestDecodeListener {

    void decodeRequestBodyBytesAfter(HttpRequest httpRequest, byte[] bytes);
}
