package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConfig {

    private static final Logger LOGGER = LoggerUtil.getLogger(ServerConfig.class);
    private final Map<String, String> staticResourceMapper = new ConcurrentHashMap<>();
    private final List<Class<Interceptor>> interceptors = new ArrayList<>();
    private boolean isSsl;
    private String host = "0.0.0.0";
    private int port;
    private boolean disableCookie;
    private int timeOut;
    private Executor executor = Executors.newFixedThreadPool(10);
    private Router router = new Router();

    public boolean isSsl() {
        return isSsl;
    }

    public void setIsSsl(boolean isSsl) {
        this.isSsl = isSsl;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public boolean isDisableCookie() {
        return disableCookie;
    }

    public void setDisableCookie(boolean disableCookie) {
        this.disableCookie = disableCookie;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Router getRouter() {
        return router;
    }

    public Interceptor getNextInterceptor(Class interceptor) {
        try {
            boolean flag = false;
            for (Class interceptor1 : interceptors) {
                if (flag) {
                    return (Interceptor) interceptor1.newInstance();
                }
                if (interceptor.getSimpleName().equals(interceptor1.getSimpleName())) {
                    flag = true;
                }
            }

        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Class<Interceptor>> getInterceptors() {
        return interceptors;
    }

    public void addInterceptor(Class interceptor) {
        try {

            if (interceptor.newInstance() instanceof Interceptor) {
                synchronized (interceptors) {
                    boolean flag = false;
                    for (Class<Interceptor> inter : interceptors) {
                        if (interceptor.toString().equals(inter.toString())) {
                            flag = true;
                        }
                    }
                    if (!flag) {
                        interceptors.add(interceptor);
                    }
                }
            } else {
                LOGGER.log(Level.SEVERE, "the class " + interceptor.getCanonicalName() + " not implements Interceptor");
            }
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void addStaticResourceMapper(String path, String locationPath) {
        staticResourceMapper.put(path, locationPath);
    }

    public Map<String, String> getStaticResourceMapper() {
        return staticResourceMapper;
    }

}
