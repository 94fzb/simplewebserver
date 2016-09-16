package com.hibegin.http.server.config;

public abstract class AbstractServerConfig {

    public abstract ServerConfig getServerConfig();

    public abstract RequestConfig getRequestConfig();

    public abstract ResponseConfig getResponseConfig();

}
