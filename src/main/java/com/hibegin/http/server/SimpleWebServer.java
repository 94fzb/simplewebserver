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
import com.hibegin.http.server.handler.CheckRequestListenerThread;
import com.hibegin.http.server.handler.HttpRequestHandlerThread;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);

    private Selector selector;
    private ServerConfig serverConfig;
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private ServerContext serverContext = new ServerContext();
    private List<HttpRequestHandlerThread> timeoutCheckRequestHandlerList = new CopyOnWriteArrayList<>();
    private Thread checkCloseTimeoutRequestThread = new Thread();
    private CheckRequestListenerThread checkRequestListenerThread = new CheckRequestListenerThread("Call-Request-Listener-Thread");

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
    }

    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new PlainReadWriteSelectorHandler(channel, key, false);
    }

    @Override
    public void listener() {
        if (selector == null) {
            return;
        }
        //开始初始化一些配置
        serverContext.init();
        if (enableRequestListener()) {
            checkRequestListenerThread.start();
        }
        if (checkCloseTimeoutRequestThread == null || checkCloseTimeoutRequestThread.isInterrupted()) {
            tryCheckConnectTimeoutRequest();
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

                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    SocketChannel channel = null;
                    if (!key.isValid() || !key.channel().isOpen()) {
                        LOGGER.log(Level.WARNING, "error key " + key);
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
            SocketAddress socketAddress = channel.socket().getRemoteSocketAddress();
            HttpRequestHandlerThread requestHandlerThread = null;
            try {
                ReadWriteSelectorHandler handler;
                if (codec == null) {
                    handler = getReadWriteSelectorHandlerInstance(channel, key);
                    codec = new HttpRequestDecoderImpl(socketAddress, requestConfig, serverContext, handler);
                    serverContext.getHttpDeCoderMap().put(channel, codec);
                } else {
                    handler = codec.getRequest().getHandler();
                }
                requestHandlerThread = new HttpRequestHandlerThread(codec, responseConfig, serverContext);
                if (enableConnectTimeout()) {
                    timeoutCheckRequestHandlerList.add(requestHandlerThread);
                }
                // 数据不完整时, 跳过当前循环等待下一个请求
                boolean exception = false;
                try {
                    ByteBuffer byteBuffer = handler.handleRead();
                    byte[] bytes = BytesUtil.subBytes(byteBuffer.array(), 0, byteBuffer.array().length - byteBuffer.remaining());
                    if (!codec.doDecode(bytes)) {
                        return false;
                    }
                } catch (EOFException e) {
                    //do nothing
                    handleException(key, codec, new HttpRequestHandlerThread(codec, responseConfig, serverContext), 400);
                    exception = true;
                } catch (UnSupportMethodException e) {
                    LOGGER.log(Level.INFO, "", e);
                    handleException(key, codec, new HttpRequestHandlerThread(codec, responseConfig, serverContext), 400);
                    exception = true;
                } catch (ContentLengthTooLargeException e) {
                    handleException(key, codec, requestHandlerThread, 413);
                    exception = true;
                } catch (Exception e) {
                    handleException(key, codec, requestHandlerThread, 500);
                    exception = true;
                }
                if (channel.isConnected() && !exception) {
                    if (enableRequestListener()) {
                        //清除老的请求
                        HttpRequestHandlerThread oldHttpRequestHandlerThread = checkRequestListenerThread.getChannelHttpRequestHandlerThreadMap().get(channel);
                        if (oldHttpRequestHandlerThread != null) {
                            oldHttpRequestHandlerThread.interrupt();
                        }
                        checkRequestListenerThread.getChannelHttpRequestHandlerThreadMap().put(channel, requestHandlerThread);
                    }
                    serverConfig.getExecutor().execute(requestHandlerThread);
                    if (codec.getRequest().getMethod() != HttpMethod.CONNECT) {
                        codec = new HttpRequestDecoderImpl(socketAddress, requestConfig, serverContext, handler);
                        serverContext.getHttpDeCoderMap().put(channel, codec);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "error", e);
                handleException(key, codec, requestHandlerThread, 500);
            }
        }
        return true;
    }

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, HttpRequestHandlerThread httpRequestHandlerThread, int errorCode) {
        try {
            if (httpRequestHandlerThread != null && codec != null && codec.getRequest() != null) {
                if (!httpRequestHandlerThread.getRequest().getHandler().getChannel().socket().isClosed()) {
                    HttpResponse response = new SimpleHttpResponse(codec.getRequest(), getDefaultResponseConfig());
                    response.renderCode(errorCode);
                } else {
                    callRequestListener(httpRequestHandlerThread);
                }
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

    private void tryCheckConnectTimeoutRequest() {
        if (enableConnectTimeout()) {
            final ServerConfig finalServerConfig = serverConfig;
            checkCloseTimeoutRequestThread = new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("Call-Request-Timeout-Listener");
                    try {
                        while (!isInterrupted()) {
                            List<HttpRequestHandlerThread> removeHttpRequestList = new ArrayList<>();
                            for (HttpRequestHandlerThread handler : timeoutCheckRequestHandlerList) {
                                if (handler.getRequest().getHandler().getChannel().socket().isClosed()) {
                                    removeHttpRequestList.add(handler);
                                } else {
                                    if (System.currentTimeMillis() - handler.getRequest().getCreateTime() > finalServerConfig.getTimeOut() * 1000) {
                                        handler.getResponse().renderCode(504);
                                        removeHttpRequestList.add(handler);
                                    }
                                }
                            }
                            timeoutCheckRequestHandlerList.removeAll(removeHttpRequestList);
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    }
                }
            };
            checkRequestListenerThread.start();
        }
    }

    private boolean enableRequestListener() {
        return !serverContext.getServerConfig().getHttpRequestListenerList().isEmpty();
    }

    private void callRequestListener(HttpRequestHandlerThread httpRequestHandlerThread) {
        for (HttpRequestListener requestListener : serverContext.getServerConfig().getHttpRequestListenerList()) {
            requestListener.destroy(httpRequestHandlerThread.getRequest(), httpRequestHandlerThread.getResponse());
        }
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
