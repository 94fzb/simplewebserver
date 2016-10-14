package com.hibegin.http.server.execption;

public class ContentLengthTooLargeException extends RuntimeException {

    public ContentLengthTooLargeException(String message) {
        super(message);
    }
}
