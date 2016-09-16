package com.hibegin.http.server.impl;

import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ServerConfig;

import java.nio.channels.Channel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerContext {

    private ServerConfig serverConfig;

    private Map<Channel, HttpRequestDeCoder> httpDeCoderMap = new ConcurrentHashMap<>();

    public Map<Channel, HttpRequestDeCoder> getHttpDeCoderMap() {
        return httpDeCoderMap;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }
}
