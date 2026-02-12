package com.hibegin.http.server.execption;

public class NotFindResourceException extends HttpCodeException {

    public NotFindResourceException(String message) {
        super(message);
    }

    @Override
    public int getCode() {
        return 404;
    }
}
