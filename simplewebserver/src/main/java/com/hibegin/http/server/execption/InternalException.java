package com.hibegin.http.server.execption;

public class InternalException extends HttpCodeException {

    public InternalException(Throwable cause) {
        super(cause);
    }

    public InternalException(String message) {
        super(message);
    }

    public InternalException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int getCode() {
        return 500;
    }
}
