package com.hibegin.http.server.config;

public class ResponseConfig {

    private boolean isGzip;
    private boolean disableCookie;
    private String charSet = "UTF-8";

    public boolean isGzip() {
        return isGzip;
    }

    public void setIsGzip(boolean isGzip) {
        this.isGzip = isGzip;
    }

    public boolean isDisableCookie() {
        return disableCookie;
    }

    public void setDisableCookie(boolean disableCookie) {
        this.disableCookie = disableCookie;
    }

    public String getCharSet() {
        return charSet;
    }

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }
}
