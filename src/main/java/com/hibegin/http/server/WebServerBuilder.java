package com.hibegin.http.server;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.util.logging.Logger;

public class WebServerBuilder {

    private static final Logger LOGGER = LoggerUtil.getLogger(WebServerBuilder.class);

    private RequestConfig requestConfig;

    private ResponseConfig responseConfig;

    private ServerConfig serverConfig;

    private SimpleWebServer socketServer;

    private WebServerBuilder(Builder builder) {
        this.serverConfig = builder.serverConfig;
        this.responseConfig = builder.responseConfig;
        this.requestConfig = builder.requestConfig;
    }

    public void start() {
        if (create()) {
            socketServer.listener();
        }
    }

    private boolean create() {
        if (serverConfig == null) {
            serverConfig = new ServerConfig();
        }
        if (serverConfig.getInterceptors().isEmpty()) {
            serverConfig.addInterceptor(MethodInterceptor.class);
        }
        if (serverConfig.isSsl()) {
            socketServer = new SimpleHttpsWebServer(serverConfig, requestConfig, responseConfig);
        } else {
            socketServer = new SimpleWebServer(serverConfig, requestConfig, responseConfig);
        }
        boolean createSuccess;
        if (serverConfig.getPort() != 0) {
            createSuccess = socketServer.create(serverConfig.getPort());
        } else {
            createSuccess = socketServer.create();
        }
        return createSuccess;
    }

    public boolean startWithThread() {
        boolean created = create();
        if (created) {
            new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName(ServerInfo.getName() + "-Main-Thread");
                    WebServerBuilder.this.socketServer.listener();
                }
            }.start();
        }
        return created;
    }

    public static class Builder {

        private RequestConfig requestConfig;

        private ResponseConfig responseConfig;

        private ServerConfig serverConfig;

        public Builder() {
        }

        public Builder requestConfig(RequestConfig requestConfig) {
            this.requestConfig = requestConfig;
            return this;
        }

        public Builder responseConfig(ResponseConfig responseConfig) {
            this.responseConfig = responseConfig;
            return this;
        }

        public Builder serverConfig(ServerConfig serverConfig) {
            this.serverConfig = serverConfig;
            return this;
        }

        public Builder config(AbstractServerConfig config) {
            serverConfig(config.getServerConfig());
            responseConfig(config.getResponseConfig());
            requestConfig(config.getRequestConfig());
            return this;
        }

        public WebServerBuilder build() {
            return new WebServerBuilder(this);
        }
    }

}
