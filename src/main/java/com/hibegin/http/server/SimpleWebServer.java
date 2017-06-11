package com.hibegin.http.server;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.handler.CheckRequestRunnable;
import com.hibegin.http.server.handler.HttpRequestHandlerThread;
import com.hibegin.http.server.handler.PlainReadWriteSelectorHandler;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.MethodInterceptor;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.AbstractMap;
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
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private ServerContext serverContext = new ServerContext();
    private File pidFile;
    private Map<Socket, HttpRequestHandlerThread> socketHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        if (serverConfig == null) {
            serverConfig = new ServerConfig();
            serverConfig.setDisableCookie(Boolean.valueOf(ConfigKit.get("server.disableCookie", requestConfig.isDisableCookie()).toString()));
        }
        if (serverConfig.getTimeOut() == 0 && ConfigKit.contains("server.timeout")) {
            serverConfig.setTimeOut(Integer.parseInt(ConfigKit.get("server.timeout", 60).toString()));
        }
        if (serverConfig.getPort() == 0) {
            serverConfig.setPort(ConfigKit.getServerPort());
        }
        this.serverConfig = serverConfig;
        if (requestConfig == null) {
            this.requestConfig = getDefaultRequestConfig();
        } else {
            this.requestConfig = requestConfig;
        }
        if (responseConfig == null) {
            this.responseConfig = getDefaultResponseConfig();
        } else {
            this.responseConfig = responseConfig;
        }
        serverContext.setServerConfig(serverConfig);
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

        LOGGER.info(ServerInfo.getName() + " is run versionStr -> " + ServerInfo.getVersion());
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
        ScheduledExecutorService scheduledExecutorService = null;
        while (selector.isOpen()) {
            //防止检查线程被jvm杀死
            if (scheduledExecutorService == null || scheduledExecutorService.isShutdown()) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                checkRequestRunnable = new CheckRequestRunnable(serverConfig.getTimeOut(), serverContext, socketHttpRequestHandlerThreadMap);
                scheduledExecutorService.scheduleAtFixedRate(checkRequestRunnable, 0, 100, TimeUnit.MILLISECONDS);
            }
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
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
                        channel = (SocketChannel) key.channel();
                        if (!handleRequest(key, channel)) continue;
                    }
                    iterator.remove();
                }
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    private boolean handleRequest(SelectionKey key, SocketChannel channel) throws IOException {
        if (channel != null && channel.isOpen()) {
            Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = serverContext.getHttpDeCoderMap().get(channel.socket());
            HttpRequestHandlerThread requestHandlerThread = null;
            ReadWriteSelectorHandler handler;
            if (codecEntry == null) {
                handler = getReadWriteSelectorHandlerInstance(channel, key);
                HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, serverContext, handler);
                codecEntry = new AbstractMap.SimpleEntry<HttpRequestDeCoder, HttpResponse>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                serverContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
            } else {
                handler = codecEntry.getKey().getRequest().getHandler();
            }
            // 数据不完整时, 跳过当前循环等待下一个请求
            boolean exception = false;
            try {
                byte[] bytes = handler.handleRead().array();
                if (!codecEntry.getKey().doDecode(bytes)) {
                    return false;
                }
                requestHandlerThread = new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext);
            } catch (EOFException e) {
                //do nothing
                handleException(key, codecEntry.getKey(), null, 400);
                exception = true;
            } catch (UnSupportMethodException | IOException e) {
                //LOGGER.log(Level.SEVERE, "", e);
                handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 400);
                exception = true;
            } catch (ContentLengthTooLargeException e) {
                handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 413);
                exception = true;
            } catch (Exception e) {
                handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 500);
                exception = true;
                LOGGER.log(Level.SEVERE, "", e);
            }
            if (channel.isConnected() && !exception) {
                if (codecEntry.getKey().getRequest().getMethod() != HttpMethod.CONNECT) {
                    HttpRequestHandlerThread oldHttpRequestHandlerThread = socketHttpRequestHandlerThreadMap.get(channel.socket());
                    //清除老的请求
                    if (oldHttpRequestHandlerThread != null) {
                        oldHttpRequestHandlerThread.interrupt();
                    }
                    socketHttpRequestHandlerThreadMap.put(channel.socket(), requestHandlerThread);
                    serverConfig.getExecutor().execute(requestHandlerThread);
                    serverContext.getHttpDeCoderMap().remove(channel.socket());
                } else {
                    HttpRequestHandlerThread oldHttpRequestHandlerThread = socketHttpRequestHandlerThreadMap.get(channel.socket());
                    if (oldHttpRequestHandlerThread == null) {
                        socketHttpRequestHandlerThreadMap.put(channel.socket(), requestHandlerThread);
                        serverConfig.getExecutor().execute(requestHandlerThread);
                    }
                }
            }
        }
        return true;
    }

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, HttpRequestHandlerThread httpRequestHandlerThread, int errorCode) {
        try {
            if (httpRequestHandlerThread != null && codec != null && codec.getRequest() != null) {
                if (!httpRequestHandlerThread.getRequest().getHandler().getChannel().socket().isClosed()) {
                    httpRequestHandlerThread.getResponse().renderCode(errorCode);
                }
                httpRequestHandlerThread.interrupt();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "error", e);
        } finally {
            try {
                key.channel().close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "error", e);
            }
            key.cancel();
        }
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
        config.setRouter(serverConfig.getRouter());
        config.setIsSsl(serverConfig.isSsl());
        return config;
    }

    public CheckRequestRunnable getCheckRequestRunnable() {
        return checkRequestRunnable;
    }
}
