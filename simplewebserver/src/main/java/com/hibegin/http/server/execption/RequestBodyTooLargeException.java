package com.hibegin.http.server.execption;

public class RequestBodyTooLargeException extends RuntimeException {

    public RequestBodyTooLargeException(String message) {
        super(message);
    }
}
