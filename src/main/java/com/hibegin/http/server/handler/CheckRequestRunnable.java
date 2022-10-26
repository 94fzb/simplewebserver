package com.hibegin.http.server.handler;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class CheckRequestRunnable implements Runnable {

    private final Map<SocketChannel, HttpRequestHandlerRunnable> channelHttpRequestHandlerThreadMap;
    private final ApplicationContext applicationContext;

    public CheckRequestRunnable(ApplicationContext applicationContext) {
        this.channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
        this.applicationContext = applicationContext;
    }

    private int getRequestTimeout() {
        return applicationContext.getServerConfig().getTimeout();
    }

    @Override
    public void run() {
        clearRequestListener(getClosedRequestSocketSet());
        clearRequestDecode(getClosedDecodedSocketSet());
    }

    private void clearRequestListener(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socket : removeHttpRequestList) {
            try {
                socket.socket().close();
                socket.close();
            } catch (IOException e) {
                //ignore
            }
            if (channelHttpRequestHandlerThreadMap.get(socket) != null) {
                channelHttpRequestHandlerThreadMap.get(socket).close();
                channelHttpRequestHandlerThreadMap.remove(socket);
            }
        }
    }

    private Set<SocketChannel> getClosedRequestSocketSet() {
        Set<SocketChannel> socketChannels = new CopyOnWriteArraySet<>();
        for (Map.Entry<SocketChannel, HttpRequestHandlerRunnable> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            SocketChannel socketChannel = entry.getKey();
            if (!socketChannel.isOpen()) {
                socketChannels.add(entry.getKey());
                continue;
            }
            if (getRequestTimeout() > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > getRequestTimeout() * 1000L) {
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

    public Map<SocketChannel, HttpRequestHandlerRunnable> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }
}
