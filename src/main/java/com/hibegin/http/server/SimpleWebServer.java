package com.hibegin.http.server;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.handler.HttpRequestHandler;
import com.hibegin.http.server.handler.PlainReadWriteSelectorHandler;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.impl.HttpMethod;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);

    private Selector selector;
    private ServerConfig serverConfig;
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private ServerContext serverContext = new ServerContext();
    private List<HttpRequestHandler> timeoutCheckRequestHandlerList = new ArrayList<>();
    private List<HttpRequestHandler> requestListenerCheckRequestHandlerList = new ArrayList<>();
    private Thread checkRequestListenerThread;
    private Thread checkCloseTimeoutRequestThread;

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        if (requestConfig == null) {
            requestConfig = new RequestConfig();
        }
        if (responseConfig == null) {
            responseConfig = new ResponseConfig();
        }
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        if (serverConfig == null) {
            serverConfig = new ServerConfig();
            serverConfig.setDisableCookie(Boolean.valueOf(ConfigKit.get("server.disableCookie", requestConfig.isDisableCookie()).toString()));
        }
        this.serverConfig = serverConfig;
        this.requestConfig = getDefaultRequestConfig();
        this.responseConfig = getDefaultResponseConfig();
        if (serverConfig.getTimeOut() == 0 && ConfigKit.contains("server.timeout")) {
            serverConfig.setTimeOut(Integer.parseInt(ConfigKit.get("server.timeout", 60).toString()));
        }
        if (serverConfig.getPort() == 0) {
            serverConfig.setPort(ConfigKit.getServerPort());
        }
        serverContext.setServerConfig(serverConfig);
        serverContext.init();
    }

    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new PlainReadWriteSelectorHandler(channel, key, false);
    }

    @Override
    public void listener() {
        if (selector == null) {
            return;
        }
        LOGGER.info(ServerInfo.getName() + " is run versionStr -> " + ServerInfo.getVersion());
        LOGGER.log(Level.INFO, serverConfig.getRouter().toString());
        try {
            EnvKit.savePid(PathUtil.getRootPath() + "/sim.pid");
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "save pid error", e);
        }
        while (true) {
            try {
                if (checkRequestListenerThread == null || !checkRequestListenerThread.isAlive()) {
                    tryStartRequestListenerCheckThread();
                }
                if (checkCloseTimeoutRequestThread == null || !checkCloseTimeoutRequestThread.isAlive()) {
                    tryCheckConnectTimeoutRequest();
                }
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    SocketChannel channel = null;
                    if (!key.isValid() || !key.channel().isOpen()) {
                        LOGGER.log(Level.WARNING, "error key" + key);
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

    private boolean handleRequest(SelectionKey key, SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            HttpRequestDeCoder codec = serverContext.getHttpDeCoderMap().get(channel);
            ReadWriteSelectorHandler handler = null;
            SocketAddress socketAddress = channel.socket().getRemoteSocketAddress();
            try {
                if (codec == null) {
                    handler = getReadWriteSelectorHandlerInstance(channel, key);
                    codec = new HttpRequestDecoderImpl(socketAddress, requestConfig, serverContext, handler);
                    serverContext.getHttpDeCoderMap().put(channel, codec);
                } else {
                    handler = codec.getRequest().getHandler();
                }
                ByteBuffer byteBuffer = handler.handleRead();
                byte[] bytes = BytesUtil.subBytes(byteBuffer.array(), 0, byteBuffer.array().length - byteBuffer.remaining());
                // 数据不完整时, 跳过当前循环等待下一个请求
                if (!codec.doDecode(bytes)) {
                    return false;
                }
                HttpRequestHandler requestHandler = new HttpRequestHandler(codec, responseConfig, serverContext);
                if (enableConnectTimeout()) {
                    timeoutCheckRequestHandlerList.add(requestHandler);
                }
                if (enableRequestListener()) {
                    requestListenerCheckRequestHandlerList.add(requestHandler);
                }
                serverConfig.getExecutor().execute(requestHandler);
                if (codec.getRequest().getMethod() != HttpMethod.CONNECT) {
                    codec = new HttpRequestDecoderImpl(socketAddress, requestConfig, serverContext, handler);
                    serverContext.getHttpDeCoderMap().put(channel, codec);
                }
            } catch (EOFException e) {
                //do nothing
                handleException(key, codec, null, 400);
            } catch (ContentLengthTooLargeException e) {
                handleException(key, codec, handler, 413);
            } catch (UnSupportMethodException e) {
                handleException(key, codec, handler, 400);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "error", e);
                handleException(key, codec, handler, 500);
            }
        }
        return true;
    }

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, ReadWriteSelectorHandler handler, int errorCode) {
        try {
            if (handler != null && codec != null && codec.getRequest() != null) {
                HttpResponse response = new SimpleHttpResponse(codec.getRequest(), getDefaultResponseConfig());
                response.renderCode(errorCode);
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

    }

    @Override
    public void create() {
        try {
            create(serverConfig.getPort());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    public void create(int port) throws IOException {
        final ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(serverConfig.getHost(), port));
        serverChannel.configureBlocking(false);
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        LOGGER.info(ServerInfo.getName() + " listening on port -> " + port);
        //timeout
        tryCheckConnectTimeoutRequest();
    }

    private void tryCheckConnectTimeoutRequest() {
        if (enableConnectTimeout()) {
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            final ServerConfig finalServerConfig = serverConfig;
            checkCloseTimeoutRequestThread =new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("Call-Request-Timeout-Listener");
                    List<HttpRequestHandler> removeHttpRequestList = new ArrayList<>();
                    for (HttpRequestHandler handler : timeoutCheckRequestHandlerList) {
                        if (handler.getRequest().getHandler().getChannel().socket().isClosed()) {
                            removeHttpRequestList.add(handler);
                        }
                        if (System.currentTimeMillis() - handler.getRequest().getCreateTime() > finalServerConfig.getTimeOut() * 1000) {
                            handler.getResponse().renderCode(504);
                            removeHttpRequestList.add(handler);
                        }
                    }
                    for (HttpRequestHandler handler : removeHttpRequestList) {
                        timeoutCheckRequestHandlerList.remove(handler);
                    }
                }
            };
            scheduledExecutorService.scheduleWithFixedDelay(checkRequestListenerThread, 1, 1, TimeUnit.SECONDS);
        }
    }

    private void tryStartRequestListenerCheckThread() {
        if (enableRequestListener()) {
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            checkRequestListenerThread = new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("Call-Close-Request-Listener");
                    List<HttpRequestHandler> removeHttpRequestList = new ArrayList<>();
                    for (HttpRequestHandler handler : requestListenerCheckRequestHandlerList) {
                        if (handler.getRequest().getHandler().getChannel().socket().isClosed()) {
                            removeHttpRequestList.add(handler);
                        }
                    }
                    for (HttpRequestHandler handler : removeHttpRequestList) {
                        for (HttpRequestListener requestListener : serverContext.getServerConfig().getHttpRequestListenerList()) {
                            requestListener.destroy(handler.getRequest(), handler.getResponse());
                        }
                        requestListenerCheckRequestHandlerList.remove(handler);
                    }
                }
            };
            scheduledExecutorService.scheduleWithFixedDelay(checkRequestListenerThread, 1, 1, TimeUnit.SECONDS);
        }
    }

    private boolean enableRequestListener() {
        return !serverContext.getServerConfig().getHttpRequestListenerList().isEmpty();
    }

    private boolean enableConnectTimeout() {
        return serverConfig.getTimeOut() > 0;
    }

    private ResponseConfig getDefaultResponseConfig() {
        ResponseConfig config = new ResponseConfig();
        config.setCharSet("UTF-8");
        config.setIsGzip(responseConfig.isGzip());
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
}
