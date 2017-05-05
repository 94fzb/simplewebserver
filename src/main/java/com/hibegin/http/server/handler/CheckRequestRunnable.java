package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.impl.ServerContext;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckRequestRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(CheckRequestRunnable.class);
    private int requestTimeout;
    private Date lastAccessDate;

    private Map<SocketChannel, HttpRequestHandlerThread> channelHttpRequestHandlerThreadMap;
    private ServerContext serverContext;

    public CheckRequestRunnable(int requestTimeout, ServerContext serverContext, Map<SocketChannel, HttpRequestHandlerThread> channelHttpRequestHandlerThreadMap) {
        this.channelHttpRequestHandlerThreadMap = channelHttpRequestHandlerThreadMap;
        this.requestTimeout = requestTimeout;
        this.serverContext = serverContext;
    }

    @Override
    public void run() {
        lastAccessDate = new Date();
        LOGGER.log(Level.INFO, "Running... " + channelHttpRequestHandlerThreadMap.size());
        try {
            Set<SocketChannel> removeHttpRequestList = getSocketChannelHttpRequestHandlerThreadMap();
            clearRequestListener(removeHttpRequestList);
            clearRequestDecode(removeHttpRequestList);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "e", e);
        }
        LOGGER.log(Level.INFO, "Running... " + channelHttpRequestHandlerThreadMap.size());
    }

    private void clearRequestListener(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socketChannel : removeHttpRequestList) {
            if (channelHttpRequestHandlerThreadMap.get(socketChannel) != null) {
                channelHttpRequestHandlerThreadMap.get(socketChannel).interrupt();
                channelHttpRequestHandlerThreadMap.remove(socketChannel);
            }
        }
    }

    private Set<SocketChannel> getSocketChannelHttpRequestHandlerThreadMap() {
        Map<SocketChannel, HttpRequestHandlerThread> removeHttpRequestList = new HashMap<>();
        for (Map.Entry<SocketChannel, HttpRequestHandlerThread> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            Socket socket = entry.getKey().socket();
            if (socket.isClosed() || !socket.isConnected()) {
                removeHttpRequestList.put(entry.getKey(), entry.getValue());
            }
            if (requestTimeout > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > requestTimeout * 1000) {
                    entry.getValue().getResponse().renderCode(504);
                    removeHttpRequestList.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return removeHttpRequestList.keySet();
    }

    private void clearRequestDecode(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socketChannel : removeHttpRequestList) {
            serverContext.getHttpDeCoderMap().remove(socketChannel);
        }
    }

    public Map<SocketChannel, HttpRequestHandlerThread> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }

    public Date getLastAccessDate() {
        return lastAccessDate;
    }
}
