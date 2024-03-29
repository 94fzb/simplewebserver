package com.hibegin.http.server;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebServerBuilder {

    private static final Logger LOGGER = LoggerUtil.getLogger(WebServerBuilder.class);

    private final RequestConfig requestConfig;

    private final ResponseConfig responseConfig;

    private ServerConfig serverConfig;

    private SimpleWebServer webServer;

    private final List<Callable<Void>> onStartErrorHandles = new ArrayList<>();

    public void addStartErrorHandle(Callable<Void> callable){
        onStartErrorHandles.add(callable);
    }

    private WebServerBuilder(Builder builder) {
        this.serverConfig = builder.serverConfig;
        this.responseConfig = builder.responseConfig;
        this.requestConfig = builder.requestConfig;
    }

    public SimpleWebServer startInBackground() {
        startWithThread();
        return webServer;
    }

    public SimpleWebServer startInBackground(ThreadFactory threadFactory) {
        startWithThread(threadFactory);
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
        if (Objects.nonNull(serverConfig.getPort()) && serverConfig.getPort() >= 0) {
            createSuccess = simpleWebServer.create(serverConfig.getHost(), serverConfig.getPort());
        } else {
            createSuccess = simpleWebServer.create();
        }
        if (createSuccess) {
            this.webServer = simpleWebServer;
        }
        if(!createSuccess){
            onStartErrorHandles.forEach(e ->{
                try {
                    e.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        return createSuccess;
    }

    public boolean startWithThread() {
        startWithThread(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(ServerInfo.getName().toLowerCase() + "-main-thread");
            return thread;
        });
        return false;
    }

    public boolean startWithThread(ThreadFactory threadFactory) {
        try {
            boolean created = create();
            if (created) {
                threadFactory.newThread(() -> {
                    WebServerBuilder.this.webServer.listener();
                }).start();
            }
            return created;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return false;
    }

    public SimpleWebServer getWebServer() {
        return webServer;
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
