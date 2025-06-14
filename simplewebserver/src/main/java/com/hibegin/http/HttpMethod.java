package com.hibegin.http;

import com.hibegin.http.server.execption.UnSupportMethodException;

import java.util.Objects;

public enum HttpMethod {

    GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE"),
    CONNECT("CONNECT"), HEAD("HEAD"), TRACE("TRACE"), OPTIONS("OPTIONS");
    private final String method;

    HttpMethod(String method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method;
    }

    public static HttpMethod parseHttpMethodByRequestLine(String httpRequestLine) {
        if (Objects.isNull(httpRequestLine) || httpRequestLine.trim().isEmpty()) {
            throw new UnSupportMethodException("Empty request line");
        }
        for (HttpMethod httpMethod : HttpMethod.values()) {
            if (httpRequestLine.startsWith(httpMethod.name() + " ")) {
                return httpMethod;
            }
        }
        throw new UnSupportMethodException(httpRequestLine.substring(0, Math.min(httpRequestLine.length(), 12)));
    }
}
