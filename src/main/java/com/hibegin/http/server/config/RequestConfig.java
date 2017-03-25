package com.hibegin.http.server.config;

import com.hibegin.http.server.web.Router;

public class RequestConfig {

    private boolean isSsl;
    private boolean disableCookie;
    private String charSet = "UTF-8";
    private Router router;

    public boolean isDisableCookie() {
        return disableCookie;
    }

    public void setDisableCookie(boolean disableCookie) {
        this.disableCookie = disableCookie;
    }

    public boolean isSsl() {
        return isSsl;
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
}
