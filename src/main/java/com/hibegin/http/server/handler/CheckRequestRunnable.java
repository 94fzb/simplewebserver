package com.hibegin.http.server.handler;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;

import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckRequestRunnable implements Runnable {

    private Map<Socket, HttpRequestHandlerThread> channelHttpRequestHandlerThreadMap;
    private ApplicationContext applicationContext;
    private static final Logger LOGGER = LoggerUtil.getLogger(CheckRequestRunnable.class);

    public CheckRequestRunnable(ApplicationContext applicationContext) {
        this.channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
        this.applicationContext = applicationContext;
    }

    private Thread thread;

    private int getRequestTimeout() {
        return applicationContext.getServerConfig().getTimeout();
    }

    @Override
    public void run() {
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
            if (getRequestTimeout() > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > getRequestTimeout() * 1000) {
                    entry.getValue().getResponse().renderCode(504);
                    socketChannels.add(entry.getKey());
                }
            }
        }
        return socketChannels;
    }

    private Set<Socket> getClosedDecodedSocketSet() {
        Set<Socket> removeHttpRequestList = new CopyOnWriteArraySet<>();
        for (Map.Entry<Socket, Map.Entry<HttpRequestDeCoder, HttpResponse>> entry : applicationContext.getHttpDeCoderMap().entrySet()) {
            Socket socket = entry.getKey();
            if (socket.isClosed() || !socket.isConnected()) {
                removeHttpRequestList.add(socket);
            }
        }
        return removeHttpRequestList;
    }

    private void clearRequestDecode(Set<Socket> removeHttpRequestList) {
        for (Socket socket : removeHttpRequestList) {
            applicationContext.getHttpDeCoderMap().remove(socket);
        }
    }

    public Map<Socket, HttpRequestHandlerThread> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }
}
