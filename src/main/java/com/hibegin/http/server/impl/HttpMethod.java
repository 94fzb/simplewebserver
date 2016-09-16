package com.hibegin.http.server.impl;

public enum HttpMethod {

    PUT("PUT"), POST("POST"), GET("GET"), DELETE("DELETE"),
    CONNECT("CONNECT"), HEAD("HEAD"), TRACE("TRACE");
    private String method;

    private HttpMethod(String method) {
        this.method = method;
    }

    public String toString() {
        return method;
    }
}
