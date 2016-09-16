package com.hibegin.http.server.api;

public interface HttpRequestDeCoder {

    boolean doDecode(byte[] bytes) throws Exception;

    HttpRequest getRequest();
}
