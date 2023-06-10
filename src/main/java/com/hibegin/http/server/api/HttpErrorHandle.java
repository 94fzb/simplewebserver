package com.hibegin.http.server.api;

public interface HttpErrorHandle {

    void doHandle(HttpRequest request, HttpResponse response, Throwable e);
}
