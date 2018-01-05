package com.hibegin.http.server.handler;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.impl.ServerContext;

import java.net.Socket;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckRequestRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(CheckRequestRunnable.class);
    private int requestTimeout;
    private Date lastAccessDate;

    private Map<Socket, HttpRequestHandlerThread> channelHttpRequestHandlerThreadMap;
    private ServerContext serverContext;
    private Thread thread;

    public CheckRequestRunnable(ServerContext serverContext) {
        this.channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
        this.requestTimeout = serverContext.getServerConfig().getTimeOut();
        this.serverContext = serverContext;
    }


    @Override
    public void run() {
        lastAccessDate = new Date();
        if (EnvKit.isAndroid()) {
            if (thread != null) {
                thread.interrupt();
            }
        }
        thread = new Thread() {
            @Override
            public void run() {
                try {
                    clearRequestListener(getClosedRequestSocketSet());
                    clearRequestDecode(getClosedDecodedSocketSet());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "e", e);
                }
            }
        };
        if (EnvKit.isAndroid()) {
            thread.start();
        } else {
            thread.run();
        }
    }

    private void clearRequestListener(Set<Socket> removeHttpRequestList) {
        for (Socket socket : removeHttpRequestList) {
            if (channelHttpRequestHandlerThreadMap.get(socket) != null) {
                channelHttpRequestHandlerThreadMap.get(socket).close();
                channelHttpRequestHandlerThreadMap.remove(socket);
            }
        }
    }

    private Set<Socket> getClosedRequestSocketSet() {
        Set<Socket> socketChannels = new CopyOnWriteArraySet<>();
        for (Map.Entry<Socket, HttpRequestHandlerThread> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            Socket socket = entry.getKey();
            if (socket.isClosed()) {
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

    private Set<Socket> getClosedDecodedSocketSet() {
        Set<Socket> removeHttpRequestList = new CopyOnWriteArraySet<>();
        for (Map.Entry<Socket, Map.Entry<HttpRequestDeCoder, HttpResponse>> entry : serverContext.getHttpDeCoderMap().entrySet()) {
            Socket socket = entry.getKey();
            if (socket.isClosed() || !socket.isConnected()) {
                removeHttpRequestList.add(socket);
            }
        }
        return removeHttpRequestList;
    }

    private void clearRequestDecode(Set<Socket> removeHttpRequestList) {
        for (Socket socket : removeHttpRequestList) {
            serverContext.getHttpDeCoderMap().remove(socket);
        }
    }

    public Map<Socket, HttpRequestHandlerThread> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }

    public Date getLastAccessDate() {
        return lastAccessDate;
    }
}
