package com.hibegin.http.server.api;

public interface HttpRequestListener {

    void destroy(HttpRequest request, HttpResponse httpResponse);

    void create(HttpRequest request, HttpResponse httpResponse);
}
