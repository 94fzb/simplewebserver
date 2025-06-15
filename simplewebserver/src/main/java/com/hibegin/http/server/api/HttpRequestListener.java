package com.hibegin.http.server.api;

public interface HttpRequestListener {

    default void onDestroy(HttpRequest request, HttpResponse httpResponse) {}

    default void onHandled(HttpRequest request, HttpResponse response) {}

    default void onCreate(HttpRequest request, HttpResponse httpResponse) {}
}
