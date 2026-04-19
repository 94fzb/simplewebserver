package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.HttpCodeException;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.FrameUtil;
import com.hibegin.http.server.util.StatusCodeUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ParseHttpMessageRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(ParseHttpMessageRunnable.class);

    private final String requestEventKey;
    private final ServerConfig serverConfig;
    private final ApplicationContext applicationContext;
    private final CheckRequestRunnable checkRequestRunnable;
    private final ResponseConfig responseConfig;
    private final SocketChannel socketChannel;

    public ParseHttpMessageRunnable(String requestEventKey, ApplicationContext applicationContext, ResponseConfig responseConfig, SocketChannel socketChannel) {
        this.requestEventKey = requestEventKey;
        this.serverConfig = applicationContext.getServerConfig();
        this.applicationContext = applicationContext;
        this.checkRequestRunnable = applicationContext.getCheckRequestRunnable();
        this.responseConfig = responseConfig;
        this.socketChannel = socketChannel;
    }

    private void renderUpgradeHttp2Response(HttpResponse httpResponse) throws IOException {
        Map<String, String> upgradeHeaderMap = new LinkedHashMap<>();
        upgradeHeaderMap.put("Connection", "upgrade");
        upgradeHeaderMap.put("Upgrade", "h2c");
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(("HTTP/1.1 101 " + StatusCodeUtil.getStatusCodeDesc(101) + "\r\n").getBytes());
        for (Map.Entry<String, String> entry : upgradeHeaderMap.entrySet()) {
            bout.write((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes());
        }
        bout.write("\r\n".getBytes());
        String body = "test";
        bout.write(FrameUtil.wrapperData(body.getBytes()));
        httpResponse.send(bout, true);
    }

    private void requestExec(HttpRequestHandlerRunnable httpRequestHandlerRunnable) {
        if (Objects.isNull(httpRequestHandlerRunnable)) {
            return;
        }
        SocketChannel socket = httpRequestHandlerRunnable.getRequest().getHandler().getChannel();
        if (Objects.equals(httpRequestHandlerRunnable.getRequest().getMethod(), HttpMethod.CONNECT)) {
            HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
            if (oldHttpRequestHandlerRunnable == null) {
                checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
                serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
            }
            return;
        }
        HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
        //清除老的请求
        if (oldHttpRequestHandlerRunnable != null) {
            oldHttpRequestHandlerRunnable.close();
        }
        checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
        serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
    }

    public abstract void addBytesToQueue(SelectionKey key, SocketChannel socket, byte[] bytes, boolean toFirst) throws Exception;

    public abstract void onDone();

    @Override
    public void run() {
        RequestEvent requestEvent;
        try {
            requestEvent = serverConfig.getHybridStorage().getAndRemove(requestEventKey);
        } catch (Exception e) {
            LOGGER.severe("Decode do parse http message error " + e.getMessage());
            onDone();
            return;
        }
        SelectionKey key = requestEvent.getSelectionKey();
        HttpRequestDeCoder requestDeCoder = applicationContext.getHttpDeCoderMap().get(socketChannel);
        try {
            if (Objects.isNull(requestDeCoder)) {
                return;
            }
            Map.Entry<Boolean, ByteBuffer> booleanEntry = requestDeCoder.doDecode(ByteBuffer.wrap(requestEvent.getRequestBody()));
            if (booleanEntry.getValue().limit() > 0) {
                addBytesToQueue(key, socketChannel, booleanEntry.getValue().array(), true);
            }
            if (Objects.equals(booleanEntry.getKey(), false)) {
                return;
            }
            if (serverConfig.isSupportHttp2()) {
                renderUpgradeHttp2Response(new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
            } else {
                requestExec(new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)));
                if (requestDeCoder.getRequest().getMethod() != HttpMethod.CONNECT) {
                    requestDeCoder.doNext();
                }
            }
        } catch (HttpCodeException e) {
            HttpExceptionUtils.handleException(serverConfig, new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)), e.getCode(), e);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "", e);
            HttpExceptionUtils.handleException(serverConfig, new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)), 500, e);
        } finally {
            onDone();
        }
    }
}
