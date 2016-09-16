package com.hibegin.http.server.api;

public interface HttpRequestMethod {

    void doGet(HttpRequest request, HttpResponse response);

    void doPost(HttpRequest request, HttpResponse response);

    void doPut(HttpRequest request, HttpResponse response);
}
