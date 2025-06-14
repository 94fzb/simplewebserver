package com.hibegin.http.server;

import com.hibegin.common.io.handler.PlainReadWriteSelectorHandler;
import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.*;
import com.hibegin.http.server.handler.CheckRequestRunnable;
import com.hibegin.http.server.handler.HttpDecodeRunnable;
import com.hibegin.http.server.util.NativeImageUtils;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
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
        this.requestConfig = ObjectUtil.requireNonNullElseGet(requestConf, this::getDefaultRequestConfig);
        this.responseConfig = ObjectUtil.requireNonNullElseGet(responseConf, this::getDefaultResponseConfig);
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
        rt.addShutdownHook(new Thread(() -> this.destroy("shutdown hook")));
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    private void savePid() {
        if (Objects.nonNull(pidFile)) {
            return;
        }
        if (EnvKit.isAndroid()) {
            return;
        }
        try {
            if (Objects.nonNull(serverConfig.getPidFilePathEnvKey()) && Objects.nonNull(System.getenv(serverConfig.getPidFilePathEnvKey()))) {
                pidFile = EnvKit.savePidBySystemEnvKey(serverConfig.getPidFilePathEnvKey());
            } else {
                pidFile = new File(PathUtil.getRootPath() + "/sim.pid");
                EnvKit.savePid(pidFile.toString());
                pidFile.deleteOnExit();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "save pid error " + e.getMessage());
        }
    }

    private void tips() {
        if (tips) {
            return;
        }
        tips = true;
        LOGGER.info(serverConfig.getServerInfo() + " is run version -> " + ServerInfo.getVersion());
    }

    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel) throws IOException {
        return new PlainReadWriteSelectorHandler(channel, requestConfig.getRequestMaxBufferSize());
    }

    @Override
    public void listen() {
        if (selector == null) {
            return;
        }
        startExecHttpRequestThread();
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
                        try {
                            iterator.remove();
                        } catch (ConcurrentModificationException e) {
                            if (EnvKit.isDevMode()) {
                                LOGGER.log(Level.SEVERE, "", e);
                            }
                        }
                    }
                }
            } catch (CancelledKeyException e) {
                //ignore
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        destroy("selector closed");
    }

    /**
     * 初始化处理请求的请求
     */
    private void startExecHttpRequestThread() {
        httpDecodeRunnable = new HttpDecodeRunnable(applicationContext, this, requestConfig, responseConfig, applicationContext.getCheckRequestRunnable());
        serverConfig.getRequestCheckerExecutor().scheduleAtFixedRate(applicationContext.getCheckRequestRunnable(), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void destroy(String reason) {
        try {
            if (Objects.nonNull(selector) && !selector.isOpen()) {
                return;
            }
            if (Objects.nonNull(selector)) {
                selector.close();
            }
            if (Objects.nonNull(serverChannel)) {
                serverChannel.close();
            }
            LOGGER.info(serverConfig.getApplicationName() + " destroyed, reason " + ObjectUtil.requireNonNullElse(reason, ""));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, serverConfig.getApplicationName() + " close selector error");
        } finally {
            if (Objects.nonNull(serverConfig.getRequestCheckerExecutor())) {
                serverConfig.getRequestCheckerExecutor().shutdownNow();
            }
        }
    }

    @Override
    public boolean create() {
        return create(ObjectUtil.requireNonNullElse(serverConfig.getPort(), ConfigKit.getServerPort()));
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
            StringJoiner applicationInfo = new StringJoiner("-");
            applicationInfo.add(serverConfig.getApplicationName());
            if (Objects.nonNull(serverConfig.getApplicationVersion()) && !serverConfig.getApplicationVersion().trim().isEmpty()) {
                applicationInfo.add(serverConfig.getApplicationVersion());
            }
            LOGGER.info(applicationInfo + " listening on port -> " + serverConfig.getPort());
            if (!serverConfig.isDisablePrintWebServerInfo()) {
                tips();
            }
            //开始初始化一些配置
            applicationContext.init();
            //try init native image info
            if (serverConfig.isNativeImageAgent()) {
                NativeImageUtils.routerMethodInvoke(applicationContext, requestConfig, responseConfig);
            }
            if (!serverConfig.isDisableSavePidFile()) {
                savePid();
            }
            if (Objects.nonNull(serverConfig.getServerPortFilePathEnvKey())) {
                EnvKit.saveHttpPortToFile(serverConfig.getServerPortFilePathEnvKey(), serverConfig.getPort());
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Create " + serverConfig.getApplicationName() + " " + port + " error, " + e.getMessage());
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
