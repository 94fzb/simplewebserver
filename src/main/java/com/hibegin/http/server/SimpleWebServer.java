package com.hibegin.http.server;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.*;
import com.hibegin.http.server.handler.*;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);
    private CheckRequestRunnable checkRequestRunnable;

    private Selector selector;
    private final ServerConfig serverConfig;
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ApplicationContext applicationContext = new ApplicationContext();
    private HttpDecodeRunnable httpDecodeRunnable;

    private ServerSocketChannel serverChannel;

    private static File pidFile;
    private static boolean tips = false;

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConf, RequestConfig requestConf, ResponseConfig responseConf) {
        if (serverConf == null) {
            serverConf = new ServerConfig();
            serverConf.setDisableCookie(Boolean.parseBoolean(ConfigKit.get("server.disableCookie", requestConf.isDisableCookie()).toString()));
        }
        if (serverConf.getTimeout() == 0 && ConfigKit.contains("server.timeout")) {
            serverConf.setTimeout(Integer.parseInt(ConfigKit.get("server.timeout", 60).toString()));
        }
        if (serverConf.getPort() == 0) {
            serverConf.setPort(ConfigKit.getServerPort());
        }
        this.serverConfig = serverConf;
        if (requestConf == null) {
            this.requestConfig = getDefaultRequestConfig();
        } else {
            this.requestConfig = requestConf;
        }
        if (responseConf == null) {
            this.responseConfig = getDefaultResponseConfig();
        } else {
            this.responseConfig = responseConf;
        }
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
        applicationContext.setServerConfig(serverConf);
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
        applicationContext.init();

        tips();
        if (applicationContext.getServerConfig().getInterceptors().contains(MethodInterceptor.class)) {
            LOGGER.info(serverConfig.getRouter().toString());
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
                        if (key.isValid() && key.isAcceptable()) {
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
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    private static void tips() {
        if (!tips) {
            tips = true;
            LOGGER.info(ServerInfo.getName() + " is run version -> " + ServerInfo.getVersion());

            try {
                if (!EnvKit.isAndroid()) {
                    if (pidFile == null) {
                        pidFile = new File(PathUtil.getRootPath() + "/sim.pid");
                    }
                    EnvKit.savePid(pidFile.toString());
                    pidFile.deleteOnExit();
                }
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "save pid error " + e.getMessage());
            }
        }
    }

    /**
     * 初始化处理请求的请求
     */
    private void startExecHttpRequestThread() {
        ScheduledExecutorService checkRequestExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("check-request-thread");
                return thread;
            }
        });
        ScheduledExecutorService httpDecodeRunnableExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("http-decode-thread");
                return thread;
            }
        });
        httpDecodeRunnable = new HttpDecodeRunnable(applicationContext, this, requestConfig, responseConfig);
        //1ms for cpu
        httpDecodeRunnableExecutor.scheduleAtFixedRate(httpDecodeRunnable, 0, 1, TimeUnit.MILLISECONDS);
        checkRequestRunnable = new CheckRequestRunnable(applicationContext);
        checkRequestExecutor.scheduleAtFixedRate(checkRequestRunnable, 0, 1000, TimeUnit.MILLISECONDS);
        new Thread(ServerInfo.getName().toLowerCase() + "-request-event-loop-thread") {
            @Override
            public void run() {
                while (true) {
                    HttpRequestHandlerRunnable httpRequestHandlerRunnable = httpDecodeRunnable.getHttpRequestHandlerThread();
                    if (httpRequestHandlerRunnable != null) {
                        Socket socket = httpRequestHandlerRunnable.getRequest().getHandler().getChannel().socket();
                        if (httpRequestHandlerRunnable.getRequest().getMethod() != HttpMethod.CONNECT) {
                            HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
                            //清除老的请求
                            if (oldHttpRequestHandlerRunnable != null) {
                                oldHttpRequestHandlerRunnable.close();
                            }
                            checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
                            serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
                        } else {
                            HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
                            if (oldHttpRequestHandlerRunnable == null) {
                                checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
                                serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
                            }
                        }
                    }
                }
            }
        }.start();
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
            LOGGER.info(ServerInfo.getName() + " close success");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "close selector error");
        }
    }

    @Override
    public boolean create() {
        return create(serverConfig.getPort());
    }

    @Override
    public boolean create(int port) {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(serverConfig.getHost(), port));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            LOGGER.info(ServerInfo.getName() + " listening on port -> " + port);
            return true;
        } catch (Exception e) {
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
