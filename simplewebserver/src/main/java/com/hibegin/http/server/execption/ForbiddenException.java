package com.hibegin.http.server.execption;

public class ForbiddenException extends RuntimeException {


    public ForbiddenException(Throwable cause) {
        super(cause);
    }

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
