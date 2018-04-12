package com.hibegin.http.server;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class WebServerBuilder {

    private static final Logger LOGGER = LoggerUtil.getLogger(WebServerBuilder.class);

    private RequestConfig requestConfig;

    private ResponseConfig responseConfig;

    private ServerConfig serverConfig;

    private SimpleWebServer webServer;

    private WebServerBuilder(Builder builder) {
        this.serverConfig = builder.serverConfig;
        this.responseConfig = builder.responseConfig;
        this.requestConfig = builder.requestConfig;
    }

    public SimpleWebServer startInBackground() {
        startWithThread();
        return webServer;
    }

    public void start() {
        if (create()) {
            webServer.listener();
        }
    }

    private boolean create() {
        if (serverConfig == null) {
            serverConfig = new ServerConfig();
        }
        if (serverConfig.getInterceptors().isEmpty()) {
            serverConfig.addInterceptor(MethodInterceptor.class);
        }
        SimpleWebServer simpleWebServer;
        if (serverConfig.isSsl()) {
            simpleWebServer = new SimpleHttpsWebServer(serverConfig, requestConfig, responseConfig);
        } else {
            simpleWebServer = new SimpleWebServer(serverConfig, requestConfig, responseConfig);
        }
        boolean createSuccess;
        if (serverConfig.getPort() != 0) {
            createSuccess = simpleWebServer.create(serverConfig.getPort());
        } else {
            createSuccess = simpleWebServer.create();
        }
        if (createSuccess) {
            this.webServer = simpleWebServer;
        }
        return createSuccess;
    }

    public boolean startWithThread() {
        try {
            boolean created = create();
            if (created) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.currentThread().setName(ServerInfo.getName().toLowerCase() + "-main-thread");
                            FileCacheKit.cleanByFlag(serverConfig.getPort());
                            WebServerBuilder.this.webServer.listener();
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "", e);
                        }
                    }
                }.start();
            }
            return created;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return false;
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

    public SimpleWebServer getWebServer() {
        return webServer;
    }
}
