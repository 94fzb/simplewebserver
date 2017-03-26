package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.impl.ServerContext;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckRequestThread extends Thread {

    private static final Logger LOGGER = LoggerUtil.getLogger(CheckRequestThread.class);

    private Map<SocketChannel, HttpRequestHandlerThread> channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
    private ServerContext serverContext;

    private int requestTimeout = 0;

    public CheckRequestThread(String name, int requestTimeout, ServerContext serverContext) {
        super(name);
        this.requestTimeout = requestTimeout;
        this.serverContext = serverContext;
    }

    @Override
    public void run() {
        try {
            while (true) {
                clearRequestListener();
                clearRequestDecode();
                //LOGGER.log(Level.INFO, "Running... " + channelHttpRequestHandlerThreadMap.size());
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    private void clearRequestListener() {
        Map<SocketChannel, HttpRequestHandlerThread> removeHttpRequestList = new HashMap<>();
        for (Map.Entry<SocketChannel, HttpRequestHandlerThread> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            if (entry.getKey().socket().isClosed() || !entry.getKey().isConnected()) {
                removeHttpRequestList.put(entry.getKey(), entry.getValue());
            }
            if (requestTimeout > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > requestTimeout * 1000) {
                    entry.getValue().getResponse().renderCode(504);
                    removeHttpRequestList.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<SocketChannel, HttpRequestHandlerThread> entry : removeHttpRequestList.entrySet()) {
            entry.getValue().interrupt();
            channelHttpRequestHandlerThreadMap.remove(entry.getKey());
        }
    }

    private void clearRequestDecode() {
        Map<SocketChannel, Map.Entry<HttpRequestDeCoder, HttpResponse>> removeHttpDecodeRequestList = new HashMap<>();
        for (Map.Entry<SocketChannel, Map.Entry<HttpRequestDeCoder, HttpResponse>> entry : serverContext.getHttpDeCoderMap().entrySet()) {
            if (entry.getKey().socket().isClosed() || !entry.getKey().isConnected()) {
                removeHttpDecodeRequestList.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<SocketChannel, Map.Entry<HttpRequestDeCoder, HttpResponse>> entryMap : removeHttpDecodeRequestList.entrySet()) {
            serverContext.getHttpDeCoderMap().remove(entryMap.getKey());
        }
    }

    public Map<SocketChannel, HttpRequestHandlerThread> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }
}
