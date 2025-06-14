package com.hibegin.http;

public enum HttpVersion {

    HTTP_1_0("HTTP/1.0"),

    HTTP_1_1("HTTP/1.1");

    private final String value;

    HttpVersion(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
