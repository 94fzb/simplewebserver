package com.hibegin.http.server.execption;

public class UnSupportMethodException extends HttpCodeException {
    public UnSupportMethodException(String msg) {
        super(msg);
    }

    @Override
    public int getCode() {
        return 405;
    }
}
