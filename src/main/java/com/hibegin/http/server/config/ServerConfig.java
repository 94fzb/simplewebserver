package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.SimpleWebServer;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConfig {

    private static final Logger LOGGER = LoggerUtil.getLogger(ServerConfig.class);
    private final Map<String, Map.Entry<String, StaticResourceLoader>> staticResourceMapper = new ConcurrentHashMap<>();
    private final List<Class<? extends Interceptor>> interceptors = new ArrayList<>();
    private boolean isSsl;
    private String host = "0.0.0.0";
    private int port;
    private boolean disableCookie;
    private int timeout;
    private boolean supportHttp2;
    private String welcomeFile = "index.html";
    private Executor requestExecutor;
    private Executor decodeExecutor;
    private String sessionId = "JSESSIONID";
    private Router router = new Router();
    private StaticResourceLoader defaultStaticResourceClassLoader = new StaticResourceLoader() {
        @Override
        public InputStream getInputStream(String path) {
            return SimpleWebServer.class.getResourceAsStream(path);
        }
    };
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

    public Executor getRequestExecutor() {
        if (requestExecutor == null) {
            requestExecutor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("request-handler-thread");
                    return thread;
                }
            });
        }
        return requestExecutor;
    }

    public void setRequestExecutor(Executor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isDisableCookie() {
        return disableCookie;
    }

    public void setDisableCookie(boolean disableCookie) {
        this.disableCookie = disableCookie;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
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

    public boolean isSupportHttp2() {
        return supportHttp2;
    }

    public void setSupportHttp2(boolean supportHttp2) {
        this.supportHttp2 = supportHttp2;
    }

    public Executor getDecodeExecutor() {
        if (decodeExecutor == null) {
            decodeExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() + 1, 20, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("request-decode-thread");
                    return thread;
                }
            });
        }
        return decodeExecutor;
    }

    public void setDecodeExecutor(Executor decodeExecutor) {
        this.decodeExecutor = decodeExecutor;
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
        addStaticResourceMapper(path, locationPath, defaultStaticResourceClassLoader);
    }

    public void addStaticResourceMapper(String path, String locationPath, StaticResourceLoader resourceClassLoader) {
        String newPath = path;
        if (!path.endsWith("/")) {
            newPath = path + "/";
        }
        String newLocationPath = locationPath;
        if (!newLocationPath.endsWith("/")) {
            newLocationPath = newLocationPath + "/";
        }
        staticResourceMapper.put(newPath, new AbstractMap.SimpleEntry<>(newLocationPath, resourceClassLoader));
    }

    public Map<String, Map.Entry<String, StaticResourceLoader>> getStaticResourceMapper() {
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
