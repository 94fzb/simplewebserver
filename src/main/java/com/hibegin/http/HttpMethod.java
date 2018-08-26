package com.hibegin.http;

public enum HttpMethod {

    GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE"),
    CONNECT("CONNECT"), HEAD("HEAD"), TRACE("TRACE"), OPTIONS("OPTIONS");
    private String method;

    HttpMethod(String method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method;
    }
}
