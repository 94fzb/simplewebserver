package com.hibegin.http.server.execption;

public class NotFindResourceException extends RuntimeException {

    public NotFindResourceException(String message) {
        super(message);
    }
}
