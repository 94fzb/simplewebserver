package com.hibegin.http;

public enum HttpMethod {

    PUT("PUT"), POST("POST"), GET("GET"), DELETE("DELETE"),
    CONNECT("CONNECT"), HEAD("HEAD"), TRACE("TRACE"), OPTIONS("OPTIONS");
    private String method;

    private HttpMethod(String method) {
        this.method = method;
    }

    public String toString() {
        return method;
    }
}
