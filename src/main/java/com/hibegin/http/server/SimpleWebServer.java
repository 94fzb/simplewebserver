package com.hibegin.http.server;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.*;
import com.hibegin.http.server.handler.CheckRequestRunnable;
import com.hibegin.http.server.handler.HttpDecodeRunnable;
import com.hibegin.http.server.handler.PlainReadWriteSelectorHandler;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.HttpRequestBuilder;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);
    private static File pidFile;
    private static boolean tips = false;
    protected final ServerConfig serverConfig;
    protected final RequestConfig requestConfig;
    protected final ResponseConfig responseConfig;
    protected final ApplicationContext applicationContext;
    private Selector selector;
    private HttpDecodeRunnable httpDecodeRunnable;
    private ServerSocketChannel serverChannel;
    private ScheduledExecutorService checkRequestExecutor;

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConf, RequestConfig requestConf, ResponseConfig responseConf) {
        if (serverConf == null) {
            serverConf = new ServerConfig();
        }
        if (serverConf.getTimeout() == 0 && ConfigKit.contains("server.timeout")) {
            serverConf.setTimeout(Integer.parseInt(ConfigKit.get("server.timeout", 60).toString()));
        }

        this.serverConfig = serverConf;
        this.requestConfig = Objects.requireNonNullElseGet(requestConf, this::getDefaultRequestConfig);
        this.responseConfig = Objects.requireNonNullElseGet(responseConf, this::getDefaultResponseConfig);
        if (this.requestConfig.getMaxRequestBodySize() < 0) {
            this.requestConfig.setMaxRequestBodySize(Integer.MAX_VALUE);
        } else if (this.requestConfig.getMaxRequestBodySize() == 0) {
            this.requestConfig.setMaxRequestBodySize(ConfigKit.getMaxRequestBodySize());
        }
        if (this.requestConfig.getRouter() == null) {
            this.requestConfig.setRouter(serverConf.getRouter());
        }
        if (this.serverConfig.getHttpJsonMessageConverter() == null) {
            if (GsonHttpJsonMessageConverter.imported()) {
                this.serverConfig.setHttpJsonMessageConverter(new GsonHttpJsonMessageConverter());
            }
        }
        this.applicationContext = new ApplicationContext(serverConfig);
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread() {
            @Override
            public void run() {
                SimpleWebServer.this.destroy();
            }
        });
    }

    private void savePid() {
        try {
            if (!EnvKit.isAndroid()) {
                if (pidFile == null) {
                    pidFile = new File(PathUtil.getRootPath() + "/sim.pid");
                }
                EnvKit.savePid(pidFile.toString());
                pidFile.deleteOnExit();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "save pid error " + e.getMessage());
        }
    }

    private static void tips() {
        if (!tips) {
            tips = true;
            LOGGER.info(ServerInfo.getName() + " is run version -> " + ServerInfo.getVersion());
        }
    }

    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new PlainReadWriteSelectorHandler(channel, requestConfig.getRequestMaxBufferSize());
    }

    @Override
    public void listener() {
        if (selector == null) {
            return;
        }
        startExecHttpRequestThread(serverChannel.socket().getLocalPort());
        while (selector.isOpen()) {
            try {
                //not message, skip. to optimize high cpu
                if (selector.select(serverConfig.getSelectNowSleepTime()) <= 0) {
                    continue;
                }
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    try {
                        SelectionKey key = iterator.next();
                        if (key.isValid() && key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel channel = server.accept();
                            channel.configureBlocking(false);
                            channel.register(selector, SelectionKey.OP_READ);
                        } else if (key.isValid() && key.isReadable()) {
                            httpDecodeRunnable.doRead((SocketChannel) key.channel(), key);
                        }
                    } catch (CancelledKeyException | IOException e) {
                        //ignore，这里基本都是系统抛出来的异常了，比如连接被异常关闭，SSL握手失败
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    } finally {
                        iterator.remove();
                    }
                }
            } catch (CancelledKeyException e) {
                //ignore
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    /**
     * 初始化处理请求的请求
     */
    private void startExecHttpRequestThread(int serverPort) {
        httpDecodeRunnable = new HttpDecodeRunnable(applicationContext, this, requestConfig, responseConfig, applicationContext.getCheckRequestRunnable());
        checkRequestExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("request-checker-" + serverPort);
            return thread;
        });
        checkRequestExecutor.scheduleAtFixedRate(applicationContext.getCheckRequestRunnable(), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        try {
            if (Objects.nonNull(selector)) {
                selector.close();
            }
            if (Objects.nonNull(serverChannel)) {
                serverChannel.socket().close();
            }
            if (Objects.nonNull(checkRequestExecutor)) {
                checkRequestExecutor.shutdownNow();
            }
            LOGGER.info(ServerInfo.getName() + " close success");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "close selector error");
        }
    }

    @Override
    public boolean create() {
        return create(Objects.requireNonNullElse(serverConfig.getPort(), ConfigKit.getServerPort()));
    }

    @Override
    public boolean create(int port) {
        return create(serverConfig.getHost(), port);
    }

    @Override
    public boolean create(String hostname, int port) {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(hostname, port));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            serverConfig.setPort(serverChannel.socket().getLocalPort());
            LOGGER.info(serverConfig.getApplicationName() + " listening on port -> " + serverConfig.getPort());
            if (!serverConfig.isDisablePrintWebServerInfo()) {
                tips();
            }
            //开始初始化一些配置
            applicationContext.init();
            //try init native image info
            if (serverConfig.isNativeImageAgent()) {
                applicationContext.getServerConfig().getRouter().getRouterMap().keySet().forEach((key) -> {
                    try {
                        HttpRequest httpRequest = HttpRequestBuilder.buildRequest(HttpMethod.GET, key, "127.0.0.1", "NativeImageAgent", requestConfig, applicationContext);
                        new MethodInterceptor().doInterceptor(httpRequest, new SimpleHttpResponse(httpRequest, responseConfig));
                        LOGGER.info("Native image agent call request " + key + " success");
                    } catch (Exception e) {
                        LOGGER.warning("Native image agent call request error -> " + LoggerUtil.recordStackTraceMsg(e));
                    }
                });
            }
            savePid();
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Create server " + port + " error " + e.getMessage());
            return false;
        }
    }

    private ResponseConfig getDefaultResponseConfig() {
        ResponseConfig config = new ResponseConfig();
        if (Objects.nonNull(responseConfig)) {
            config.setEnableGzip(responseConfig.isEnableGzip());
            config.setGzipMimeTypes(responseConfig.getGzipMimeTypes());
            config.setCharSet(responseConfig.getCharSet());
        }
        return config;
    }

    private RequestConfig getDefaultRequestConfig() {
        RequestConfig config = new RequestConfig();
        config.setIsSsl(serverConfig.isSsl());
        config.setDisableSession(serverConfig.isDisableSession());
        return config;
    }

    public CheckRequestRunnable getCheckRequestRunnable() {
        return applicationContext.getCheckRequestRunnable();
    }
}
