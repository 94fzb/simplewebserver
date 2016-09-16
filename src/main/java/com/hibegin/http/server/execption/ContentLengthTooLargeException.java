package com.hibegin.http.server.execption;

/**
 * Created by xiaochun on 15-8-27.
 */
public class ContentLengthTooLargeException extends RuntimeException {

    public ContentLengthTooLargeException(String message) {
        super(message);
    }
}
