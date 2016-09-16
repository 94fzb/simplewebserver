package com.hibegin.http.server.api;

public interface Interceptor {

    boolean doInterceptor(HttpRequest request, HttpResponse response);
}
