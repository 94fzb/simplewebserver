package com.hibegin.http.server.config;

import com.hibegin.http.server.web.Router;

public class RequestConfig {

    private boolean isSsl;
    private boolean disableSession;

    public boolean isDisableSession() {
        return disableSession;
    }

    public void setDisableSession(boolean disableSession) {
        this.disableSession = disableSession;
    }

    private String charSet = "UTF-8";
    private Router router;
    private int maxRequestBodySize;
    private int maxRequestHeaderSize;
    private int requestMaxBufferSize;

    public boolean isSsl() {
        return isSsl;
    }

    public void setSsl(boolean ssl) {
        isSsl = ssl;
    }

    public void setIsSsl(boolean isSsl) {
        this.isSsl = isSsl;
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public String getCharSet() {
        return charSet;
    }

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }

    public int getMaxRequestBodySize() {
        return maxRequestBodySize;
    }

    public void setMaxRequestBodySize(int maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }

    public int getRequestMaxBufferSize() {
        if (requestMaxBufferSize == 0) {
            return 512 * 1024;
        }
        return requestMaxBufferSize;
    }

    public int getMaxRequestHeaderSize() {
        if (maxRequestHeaderSize == 0) {
            return 128 * 1024;
        }
        return Math.max(maxRequestHeaderSize, 32 * 1024);
    }

    public void setMaxRequestHeaderSize(int maxRequestHeaderSize) {
        this.maxRequestHeaderSize = maxRequestHeaderSize;
    }

    public void setRequestMaxBufferSize(int requestMaxBufferSize) {
        this.requestMaxBufferSize = requestMaxBufferSize;
    }
}
