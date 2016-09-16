package com.hibegin.http.server;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.ISocketServer;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
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
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebServer implements ISocketServer {


    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleWebServer.class);

    private Selector selector;
    private ServerConfig serverConfig;
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private ServerContext serverContext = new ServerContext();

    public SimpleWebServer() {
        this(null, null, null);
    }

    public SimpleWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        if (requestConfig == null) {
            requestConfig = new RequestConfig();
        }
        if (serverConfig == null) {
            serverConfig = new ServerConfig();
            serverConfig.setDisableCookie(Boolean.valueOf(ConfigKit.get("server.disableCookie", requestConfig.isDisableCookie()).toString()));
        }
        if (responseConfig == null) {
            responseConfig = new ResponseConfig();
        }
        this.serverConfig = serverConfig;
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        if (serverConfig.getTimeOut() == 0) {
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
        LOGGER.info("SimplerWebServer is run versionStr -> " + ServerInfo.getVersion());
        try {
            EnvKit.savePid(PathUtil.getRootPath() + "/sim.pid");
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "save Pid error", e);
        }
        while (true) {
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    SocketChannel channel;
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
                            LOGGER.log(Level.FINE, "accept connnect error", e);
                            server.socket().close();
                        }

                    } else if (key.isReadable()) {
                        channel = (SocketChannel) key.channel();
                        if (channel != null && channel.isOpen()) {
                            HttpRequestDeCoder codec = serverContext.getHttpDeCoderMap().get(channel);
                            ReadWriteSelectorHandler handler = null;
                            SocketAddress socketAddress = channel.socket().getRemoteSocketAddress();
                            try {
                                if (codec == null) {
                                    handler = getReadWriteSelectorHandlerInstance(channel, key);
                                    codec = new HttpRequestDecoderImpl(socketAddress, getDefaultRequestConfig(), serverContext, handler);
                                    serverContext.getHttpDeCoderMap().put(channel, codec);
                                } else {
                                    handler = codec.getRequest().getHandler();
                                }
                                ByteBuffer byteBuffer = handler.handleRead();
                                byte[] bytes = BytesUtil.subBytes(byteBuffer.array(), 0, byteBuffer.array().length - byteBuffer.remaining());
                                // 数据完整时, 跳过当前循环等待下一个请求
                                if (!codec.doDecode(bytes)) {
                                    continue;
                                }
                                serverConfig.getExecutor().execute(new HttpRequestHandler(codec, key, serverConfig, getDefaultResponseConfig(), serverContext));
                                if (codec.getRequest().getMethod() != HttpMethod.CONNECT) {
                                    codec = new HttpRequestDecoderImpl(socketAddress, getDefaultRequestConfig(), serverContext, handler);
                                    serverContext.getHttpDeCoderMap().put(channel, codec);
                                }
                            } catch (EOFException e) {
                                //do nothing
                                handleException(key, codec, null, 500);
                            } catch (ContentLengthTooLargeException e) {
                                handleException(key, codec, handler, 413);
                            } catch (Exception e) {
                                handleException(key, codec, handler, 500);
                            }
                        }
                    }
                    iter.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
            e.printStackTrace();
        }
    }

    public void create(int port) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(serverConfig.getHost(), port));
        serverChannel.configureBlocking(false);
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        LOGGER.info("SimplerWebServer listening on port -> " + port);
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
