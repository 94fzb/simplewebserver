package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;

import java.lang.reflect.Constructor;
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
    private final List<Class<? extends Interceptor>> interceptors = new ArrayList<>();
    private boolean isSsl;
    private String host = "0.0.0.0";
    private int port;
    private boolean disableCookie;
    private int timeOut;
    private String welcomeFile = "index.html";
    private Executor executor = Executors.newFixedThreadPool(10);
    private Router router = new Router();
    private List<HttpRequestListener> httpRequestListenerList = new ArrayList<>();

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
            LOGGER.log(Level.SEVERE, "", e);
        }
        return null;
    }

    public List<Class<? extends Interceptor>> getInterceptors() {
        return interceptors;
    }

    public void addInterceptor(Class<? extends Interceptor> interceptor) {
        if (hasNoParameterPublicConstructor(interceptor)) {
            synchronized (interceptors) {
                boolean flag = false;
                for (Class<? extends Interceptor> inter : interceptors) {
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
    }

    private boolean hasNoParameterPublicConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                return true;
            }
        }
        return false;
    }

    public void addStaticResourceMapper(String path, String locationPath) {
        String newPath = path;
        if (!path.endsWith("/")) {
            newPath = path + "/";
        }
        String newLocationPath = locationPath;
        if (!newLocationPath.endsWith("/")) {
            newLocationPath = newLocationPath + "/";
        }
        staticResourceMapper.put(newPath, newLocationPath);
    }

    public Map<String, String> getStaticResourceMapper() {
        return staticResourceMapper;
    }

    public String getWelcomeFile() {
        return welcomeFile;
    }

    public void setWelcomeFile(String welcomeFile) {
        this.welcomeFile = welcomeFile;
    }

    public void addReqeustListener(HttpRequestListener httpRequestListener) {
        httpRequestListenerList.add(httpRequestListener);
    }

    public List<HttpRequestListener> getHttpRequestListenerList() {
        return httpRequestListenerList;
    }
}
