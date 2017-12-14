package com.hibegin.http.server;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.handler.*;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);
    private CheckRequestRunnable checkRequestRunnable;

    private Selector selector;
    private ServerConfig serverConfig;
    private RequestConfig requestConf;
    private ResponseConfig responseConfig;
    private ServerContext serverContext = new ServerContext();
    private File pidFile;
    private HttpDecodeRunnable httpDecodeRunnable;

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConf, RequestConfig requestConf, ResponseConfig responseConf) {
        if (serverConf == null) {
            serverConf = new ServerConfig();
            serverConf.setDisableCookie(Boolean.valueOf(ConfigKit.get("server.disableCookie", requestConf.isDisableCookie()).toString()));
        }
        if (serverConf.getTimeOut() == 0 && ConfigKit.contains("server.timeout")) {
            serverConf.setTimeOut(Integer.parseInt(ConfigKit.get("server.timeout", 60).toString()));
        }
        if (serverConf.getPort() == 0) {
            serverConf.setPort(ConfigKit.getServerPort());
        }
        this.serverConfig = serverConf;
        if (requestConf == null) {
            this.requestConf = getDefaultRequestConfig();
        } else {
            this.requestConf = requestConf;
        }
        if (responseConf == null) {
            this.responseConfig = getDefaultResponseConfig();
        } else {
            this.responseConfig = responseConf;
        }
        if (this.requestConf.getMaxRequestBodySize() < 0) {
            this.requestConf.setMaxRequestBodySize(Integer.MAX_VALUE);
        } else if (this.requestConf.getMaxRequestBodySize() == 0) {
            this.requestConf.setMaxRequestBodySize(ConfigKit.getMaxUploadSize());
        }
        if (this.requestConf.getRouter() == null) {
            this.requestConf.setRouter(serverConf.getRouter());
        }
        serverContext.setServerConfig(serverConf);
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread() {
            @Override
            public void run() {
                SimpleWebServer.this.destroy();
            }
        });
    }

    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new PlainReadWriteSelectorHandler(channel);
    }

    @Override
    public void listener() {
        if (selector == null) {
            return;
        }
        //开始初始化一些配置
        serverContext.init();
        LOGGER.info(ServerInfo.getName() + " is run version -> " + ServerInfo.getVersion());
        if (serverContext.getServerConfig().getInterceptors().contains(MethodInterceptor.class)) {
            LOGGER.info(serverConfig.getRouter().toString());
        }
        try {
            if (pidFile == null) {
                pidFile = new File(PathUtil.getRootPath() + "/sim.pid");
            }
            EnvKit.savePid(pidFile.toString());
            pidFile.deleteOnExit();
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "save pid error " + e.getMessage());
        }
        startExecHttpRequestThread();
        while (selector.isOpen()) {
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    try {
                        SelectionKey key = iterator.next();
                        SocketChannel channel = null;
                        if (!key.isValid() || !key.channel().isOpen()) {
                            continue;
                        } else if (key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            try {
                                channel = server.accept();
                                if (channel != null) {
                                    channel.configureBlocking(false);
                                    channel.register(selector, SelectionKey.OP_READ);
                                }
                            } catch (IOException e) {
                                LOGGER.log(Level.SEVERE, "accept connect error", e);
                                if (channel != null) {
                                    key.cancel();
                                    channel.close();
                                }
                            }
                        } else if (key.isReadable()) {
                            httpDecodeRunnable.addTask((SocketChannel) key.channel(), key);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    } finally {
                        iterator.remove();
                    }
                }
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    /**
     * 初始化处理请求的请求
     */
    private void startExecHttpRequestThread() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);
        checkRequestRunnable = new CheckRequestRunnable(serverConfig.getTimeOut(), serverContext);
        httpDecodeRunnable = new HttpDecodeRunnable(serverContext, this, requestConf, responseConfig, serverConfig);
        scheduledExecutorService.scheduleAtFixedRate(checkRequestRunnable, 0, 100, TimeUnit.MILLISECONDS);
        scheduledExecutorService.scheduleAtFixedRate(httpDecodeRunnable, 0, 1, TimeUnit.MILLISECONDS);
        new Thread(ServerInfo.getName().toLowerCase() + "-http-request-exec-thread") {
            @Override
            public void run() {
                while (true) {
                    HttpRequestHandlerThread requestHandlerThread = httpDecodeRunnable.getHttpRequestHandlerThread();
                    if (requestHandlerThread != null) {
                        Socket socket = requestHandlerThread.getRequest().getHandler().getChannel().socket();
                        if (requestHandlerThread.getRequest().getMethod() != HttpMethod.CONNECT) {
                            HttpRequestHandlerThread oldHttpRequestHandlerThread = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
                            //清除老的请求
                            if (oldHttpRequestHandlerThread != null) {
                                oldHttpRequestHandlerThread.interrupt();
                            }
                            checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, requestHandlerThread);
                            serverConfig.getExecutor().execute(requestHandlerThread);
                        } else {
                            HttpRequestHandlerThread oldHttpRequestHandlerThread = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
                            if (oldHttpRequestHandlerThread == null) {
                                checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, requestHandlerThread);
                                serverConfig.getExecutor().execute(requestHandlerThread);
                            }
                        }
                    }
                }
            }
        }.start();
    }

    @Override
    public void destroy() {
        if (selector == null) {
            return;
        }
        try {
            selector.close();
            LOGGER.info("close webServer success");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "close selector error");
        }
    }

    @Override
    public boolean create() {
        return create(serverConfig.getPort());
    }

    public boolean create(int port) {
        try {
            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(serverConfig.getHost(), port));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            LOGGER.info(ServerInfo.getName() + " listening on port -> " + port);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
            return false;
        }
    }

    private ResponseConfig getDefaultResponseConfig() {
        ResponseConfig config = new ResponseConfig();
        config.setCharSet("UTF-8");
        if (responseConfig == null) {
            config.setIsGzip(false);
        } else {
            config.setIsGzip(responseConfig.isGzip());
        }
        config.setDisableCookie(serverConfig.isDisableCookie());
        return config;
    }

    private RequestConfig getDefaultRequestConfig() {
        RequestConfig config = new RequestConfig();
        config.setDisableCookie(serverConfig.isDisableCookie());
        config.setIsSsl(serverConfig.isSsl());
        return config;
    }

    public CheckRequestRunnable getCheckRequestRunnable() {
        return checkRequestRunnable;
    }
}
