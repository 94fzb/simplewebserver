package com.hibegin.http.server;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.FileCacheKit;

import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApplicationContext {

    private static final Logger LOGGER = LoggerUtil.getLogger(ApplicationContext.class);
    private boolean init;
    private List<Interceptor> interceptors;

    private ServerConfig serverConfig;

    private final Map<Socket, Map.Entry<HttpRequestDeCoder, HttpResponse>> httpDeCoderMap = new ConcurrentHashMap<>();

    public Map<Socket, Map.Entry<HttpRequestDeCoder, HttpResponse>> getHttpDeCoderMap() {
        return httpDeCoderMap;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    void init() {
        if (!init) {
            //清空tmp目录
            FileCacheKit.cleanByFlag(serverConfig.getPort());
            init = true;
            interceptors = new ArrayList<>();
            for (Class<? extends Interceptor> interceptorClazz : serverConfig.getInterceptors()) {
                try {
                    Interceptor interceptor = interceptorClazz.getDeclaredConstructor().newInstance();
                    interceptors.add(interceptor);
                } catch (InstantiationException | IllegalAccessException e) {
                    LOGGER.log(Level.SEVERE, "init interceptor error", e);
                } catch (NoSuchMethodException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
