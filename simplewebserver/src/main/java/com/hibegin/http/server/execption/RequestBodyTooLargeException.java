package com.hibegin.http.server.execption;

public class RequestBodyTooLargeException extends HttpCodeException {

    public RequestBodyTooLargeException(String message) {
        super(message);
    }

    @Override
    public int getCode() {
        return 413;
    }
}
