package com.hibegin.http.server.api;

public interface HandleAbleInterceptor extends Interceptor {
    boolean isHandleAble(HttpRequest request);
}