package com.hibegin.http.server.config;

import com.hibegin.common.HybridStorage;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequestDecodeListener;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Router;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConfig {

    private static final Logger LOGGER = LoggerUtil.getLogger(ServerConfig.class);
    private final Map<String, Map.Entry<String, StaticResourceLoader>> staticResourceMapper = new ConcurrentHashMap<>();
    private final List<Class<? extends Interceptor>> interceptors = new ArrayList<>();
    private final Router router = new Router();
    private final Map<String, Object> attr = new ConcurrentHashMap<>();
    private final Map<Integer, HttpErrorHandle> httpErrorHandleMap = new ConcurrentHashMap<>();
    private final StaticResourceLoader defaultStaticResourceClassLoader = ServerConfig.class::getResourceAsStream;
    private final List<HttpRequestListener> httpRequestListenerList = new ArrayList<>();
    private boolean ssl;
    private String host = "0.0.0.0";
    private Integer port;
    private String contextPath = "";

    private boolean disableSession;
    private int timeout;
    private boolean supportHttp2;
    private String welcomeFile = "index.html";
    private Executor requestExecutor;
    private ScheduledExecutorService requestCheckerExecutor;
    private Executor decodeExecutor;
    private String sessionId = "JSESSIONID";
    private String serverInfo;
    private boolean nativeImageAgent;
    private int selectNowSleepTime = 1;
    private HttpJsonMessageConverter httpJsonMessageConverter;
    private HttpRequestDecodeListener httpRequestDecodeListener;
    private Class<?> basicTemplateClass;
    private String applicationName;
    private String applicationVersion;
    private boolean disablePrintWebServerInfo;
    private boolean disableSavePidFile;
    private HybridStorage hybridStorage;
    private String pidFilePathEnvKey;
    private String serverPortFilePathEnvKey;

    public boolean isDisableSession() {
        return disableSession;
    }

    public ServerConfig setDisableSession(boolean disableSession) {
        this.disableSession = disableSession;
        return this;
    }

    public Map<String, Object> getAttr() {
        return attr;
    }

    public String getPidFilePathEnvKey() {
        return pidFilePathEnvKey;
    }

    public ServerConfig setPidFilePathEnvKey(String pidFilePathEnvKey) {
        this.pidFilePathEnvKey = pidFilePathEnvKey;
        return this;
    }

    public String getServerPortFilePathEnvKey() {
        return serverPortFilePathEnvKey;
    }

    public ServerConfig setServerPortFilePathEnvKey(String serverPortFilePathEnvKey) {
        this.serverPortFilePathEnvKey = serverPortFilePathEnvKey;
        return this;
    }

    public boolean isDisableSavePidFile() {
        return disableSavePidFile;
    }

    public ServerConfig setDisableSavePidFile(boolean disableSavePidFile) {
        this.disableSavePidFile = disableSavePidFile;
        return this;
    }

    public HybridStorage getHybridStorage() {
        if (Objects.isNull(hybridStorage)) {
            this.hybridStorage = new HybridStorage(Long.MAX_VALUE, PathUtil.getTempPath());
        }
        return hybridStorage;
    }

    public ServerConfig setHybridStorage(HybridStorage hybridStorage) {
        this.hybridStorage = hybridStorage;
        return this;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public ServerConfig setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
        return this;
    }

    public String getApplicationName() {
        return ObjectUtil.requireNonNullElse(applicationName, ServerInfo.getName());
    }

    public ServerConfig setApplicationName(String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    public boolean isDisablePrintWebServerInfo() {
        return disablePrintWebServerInfo;
    }

    public ServerConfig setDisablePrintWebServerInfo(boolean disablePrintWebServerInfo) {
        this.disablePrintWebServerInfo = disablePrintWebServerInfo;
        return this;
    }

    public String getServerInfo() {
        if (Objects.nonNull(serverInfo)) {
            return serverInfo;
        }
        StringJoiner sj = new StringJoiner("/");
        if (Objects.nonNull(applicationName)) {
            sj.add(applicationName);
            if (Objects.nonNull(applicationVersion)) {
                sj.add(applicationVersion);
            }
        } else {
            sj.add(ServerInfo.getName());
            sj.add(ServerInfo.getVersion());
        }
        return sj.toString();
    }

    public ServerConfig setServerInfo(String serverInfo) {
        this.serverInfo = serverInfo;
        return this;
    }

    public Class<?> getBasicTemplateClass() {
        return basicTemplateClass;
    }

    public ServerConfig setBasicTemplateClass(Class<?> basicTemplateClass) {
        this.basicTemplateClass = basicTemplateClass;
        return this;
    }

    public boolean isNativeImageAgent() {
        return nativeImageAgent;
    }

    public ServerConfig setNativeImageAgent(boolean nativeImageAgent) {
        this.nativeImageAgent = nativeImageAgent;
        return this;
    }

    public boolean isSsl() {
        return ssl;
    }

    public ServerConfig setSsl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public ServerConfig setPort(Integer port) {
        this.port = port;
        return this;
    }

    public Executor getRequestExecutor() {
        if (requestExecutor == null) {
            requestExecutor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), new ThreadFactory() {
                final AtomicInteger threadNumber = new AtomicInteger(1);
                final String namePrefix = "http-nio-" + getPort() + "-exec-";

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName(namePrefix + threadNumber.getAndIncrement());
                    return thread;
                }
            });
        }
        return requestExecutor;
    }

    public ServerConfig setRequestExecutor(Executor requestExecutor) {
        this.requestExecutor = requestExecutor;
        return this;
    }

    public ScheduledExecutorService getRequestCheckerExecutor() {
        if (requestCheckerExecutor == null) {
            requestCheckerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("request-checker-" + port);
                return thread;
            });
        }
        return requestCheckerExecutor;
    }

    public ServerConfig setRequestCheckerExecutor(ScheduledExecutorService requestCheckerExecutor) {
        this.requestCheckerExecutor = requestCheckerExecutor;
        return this;
    }

    public HttpJsonMessageConverter getHttpJsonMessageConverter() {
        return httpJsonMessageConverter;
    }

    public ServerConfig setHttpJsonMessageConverter(HttpJsonMessageConverter httpJsonMessageConverter) {
        this.httpJsonMessageConverter = httpJsonMessageConverter;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ServerConfig setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public ServerConfig setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public String getHost() {
        return host;
    }

    public ServerConfig setHost(String host) {
        this.host = host;
        return this;
    }

    public Router getRouter() {
        return router;
    }

    public boolean isSupportHttp2() {
        return supportHttp2;
    }

    public ServerConfig setSupportHttp2(boolean supportHttp2) {
        this.supportHttp2 = supportHttp2;
        return this;
    }

    public Executor getDecodeExecutor() {
        if (decodeExecutor == null) {
            decodeExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() + 1, Runtime.getRuntime().availableProcessors() * 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), new ThreadFactory() {

                private final AtomicLong count = new AtomicLong();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("request-decode-" + count.getAndIncrement());
                    return thread;
                }
            });
        }
        return decodeExecutor;
    }

    public ServerConfig setDecodeExecutor(Executor decodeExecutor) {
        this.decodeExecutor = decodeExecutor;
        return this;
    }

    public List<Class<? extends Interceptor>> getInterceptors() {
        return interceptors;
    }

    public ServerConfig addInterceptor(Class<? extends Interceptor> interceptor) {
        if (hasNoParameterPublicConstructor(interceptor)) {
            synchronized (interceptors) {
                boolean flag = false;
                for (Class<? extends Interceptor> inter : interceptors) {
                    if (interceptor.toString().equals(inter.toString())) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    interceptors.add(interceptor);
                }
            }
        } else {
            LOGGER.log(Level.SEVERE, "the class " + interceptor.getCanonicalName() + " not implements Interceptor");
        }
        return this;
    }

    private boolean hasNoParameterPublicConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                return true;
            }
        }
        return false;
    }

    public ServerConfig addStaticResourceMapper(String uriPath, String locationPath) {
        addStaticResourceMapper(uriPath, locationPath, defaultStaticResourceClassLoader);
        return this;
    }

    public ServerConfig addLocalFileStaticResourceMapper(String uriPath, String filePath) {
        addLocalFileStaticResourceMapper(uriPath, filePath, false);
        return this;
    }

    public ServerConfig addLocalFileStaticResourceMapper(String uriPath, String filePath, boolean autoIndex) {
        addStaticResourceMapper(uriPath, filePath, new LocalFileStaticResourceLoader(autoIndex, uriPath, filePath));
        return this;
    }

    public StaticResourceLoader getDefaultStaticResourceClassLoader() {
        return defaultStaticResourceClassLoader;
    }

    public ServerConfig addStaticResourceMapper(String uriPath, String locationPath, StaticResourceLoader resourceClassLoader) {
        String newPath = uriPath;
        if (!uriPath.endsWith("/")) {
            newPath = uriPath + "/";
        }
        String newLocationPath = locationPath;
        if (!newLocationPath.endsWith("/")) {
            newLocationPath = newLocationPath + "/";
        }
        staticResourceMapper.put(newPath, new AbstractMap.SimpleEntry<>(newLocationPath, resourceClassLoader));
        return this;
    }

    public Map<String, Map.Entry<String, StaticResourceLoader>> getStaticResourceMapper() {
        return staticResourceMapper;
    }

    public String getWelcomeFile() {
        return welcomeFile;
    }

    public ServerConfig setWelcomeFile(String welcomeFile) {
        this.welcomeFile = welcomeFile;
        return this;
    }

    public ServerConfig addRequestListener(HttpRequestListener httpRequestListener) {
        httpRequestListenerList.add(httpRequestListener);
        return this;
    }

    public List<HttpRequestListener> getHttpRequestListenerList() {
        return httpRequestListenerList;
    }

    public HttpRequestDecodeListener getHttpRequestDecodeListener() {
        return httpRequestDecodeListener;
    }

    public ServerConfig setHttpRequestDecodeListener(HttpRequestDecodeListener httpRequestDecodeListener) {
        this.httpRequestDecodeListener = httpRequestDecodeListener;
        return this;
    }

    public ServerConfig addErrorHandle(Integer errorCode, HttpErrorHandle errorHandle) {
        httpErrorHandleMap.put(errorCode, errorHandle);
        return this;
    }

    public HttpErrorHandle getErrorHandle(Integer errorCode) {
        return httpErrorHandleMap.get(errorCode);
    }

    public int getSelectNowSleepTime() {
        return selectNowSleepTime;
    }

    public ServerConfig setSelectNowSleepTime(int selectNowSleepTime) {
        this.selectNowSleepTime = selectNowSleepTime;
        return this;
    }

    public String getContextPath() {
        if (Objects.equals(contextPath, "/")) {
            return "";
        }
        return ObjectUtil.requireNonNullElse(contextPath, "");
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
}
