package com.hibegin.http.server.execption;

public abstract class HttpCodeException extends RuntimeException {

    public HttpCodeException() {
    }

    public HttpCodeException(String message) {
        super(message);
    }

    public HttpCodeException(Throwable cause) {
        super(cause);
    }

    public HttpCodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract int getCode();
}
