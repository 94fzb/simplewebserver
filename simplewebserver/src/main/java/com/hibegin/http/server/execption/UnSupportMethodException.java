package com.hibegin.http.server.execption;

public class UnSupportMethodException extends RuntimeException {
    public UnSupportMethodException(String msg) {
        super(msg);
    }
}
