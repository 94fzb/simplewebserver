package com.hibegin.http.server.config;

import java.util.*;

public class ResponseConfig {

    private boolean enableGzip;
    private boolean disableCookie;
    private String charSet = "UTF-8";
    private List<String> gzipMimeTypes = new ArrayList<>();
    private Map<String,String> defaultHeaders = new LinkedHashMap<>();

    public boolean isEnableGzip() {
        return enableGzip;
    }

    public void setEnableGzip(boolean enableGzip) {
        this.enableGzip = enableGzip;
    }

    public List<String> getGzipMimeTypes() {
        return gzipMimeTypes;
    }

    public void setGzipMimeTypes(List<String> gzipMimeTypes) {
        this.gzipMimeTypes = gzipMimeTypes;
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

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }
}
