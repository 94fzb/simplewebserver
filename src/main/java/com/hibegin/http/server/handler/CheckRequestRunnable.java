package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.impl.ServerContext;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
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
        try {
            clearRequestListener(getClosedRequestChannelSet());
            clearRequestDecode(getClosedDecodedChannelSet());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "e", e);
        }
    }

    private void clearRequestListener(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socketChannel : removeHttpRequestList) {
            if (channelHttpRequestHandlerThreadMap.get(socketChannel) != null) {
                channelHttpRequestHandlerThreadMap.get(socketChannel).interrupt();
                channelHttpRequestHandlerThreadMap.remove(socketChannel);
            }
        }
    }

    private Set<SocketChannel> getClosedRequestChannelSet() {
        Set<SocketChannel> socketChannels = new CopyOnWriteArraySet<>();
        for (Map.Entry<SocketChannel, HttpRequestHandlerThread> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            Socket socket = entry.getKey().socket();
            if (socket.isClosed() || !socket.isConnected()) {
                socketChannels.add(entry.getKey());
            }
            if (requestTimeout > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > requestTimeout * 1000) {
                    entry.getValue().getResponse().renderCode(504);
                    socketChannels.add(entry.getKey());
                }
            }
        }
        return socketChannels;
    }

    private Set<SocketChannel> getClosedDecodedChannelSet() {
        Set<SocketChannel> removeHttpRequestList = new CopyOnWriteArraySet<>();
        for (Map.Entry<SocketChannel, Map.Entry<HttpRequestDeCoder, HttpResponse>> entry : serverContext.getHttpDeCoderMap().entrySet()) {
            Socket socket = entry.getKey().socket();
            if (socket.isClosed() || !socket.isConnected()) {
                removeHttpRequestList.add(entry.getKey());
            }
        }
        return removeHttpRequestList;
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
