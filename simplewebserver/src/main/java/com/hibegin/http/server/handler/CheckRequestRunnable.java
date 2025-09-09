package com.hibegin.http.server.handler;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ServerConfig;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class CheckRequestRunnable implements Runnable {

    private final Map<SocketChannel, HttpRequestHandlerRunnable> channelHttpRequestHandlerThreadMap;
    private final ServerConfig serverConfig;
    private final Map<SocketChannel, HttpRequestDeCoder> httpDeCoderMap;


    public CheckRequestRunnable(ServerConfig serverConfig, Map<SocketChannel, HttpRequestDeCoder> httpDeCoderMap) {
        this.channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
        this.serverConfig = serverConfig;
        this.httpDeCoderMap = httpDeCoderMap;
    }

    private int getRequestTimeout() {
        return serverConfig.getTimeout();
    }

    @Override
    public void run() {
        clearRequestListener(getClosedRequestSocketSet());
        clearRequestDecode(getClosedDecodedSocketSet());
    }

    private void clearRequestListener(Set<HttpRequestHandlerRunnable> removeHttpRequestList) {
        for (HttpRequestHandlerRunnable handlerRunnable : removeHttpRequestList) {
            ReadWriteSelectorHandler handler = handlerRunnable.getRequest().getHandler();
            handler.close();
            handlerRunnable.close();
            SocketChannel socketChannel = handler.getChannel();
            channelHttpRequestHandlerThreadMap.remove(socketChannel);
        }
    }

    private Set<HttpRequestHandlerRunnable> getClosedRequestSocketSet() {
        Set<HttpRequestHandlerRunnable> socketChannels = new CopyOnWriteArraySet<>();
        for (Map.Entry<SocketChannel, HttpRequestHandlerRunnable> entry : channelHttpRequestHandlerThreadMap.entrySet()) {
            SocketChannel socketChannel = entry.getKey();
            if (!socketChannel.isOpen()) {
                socketChannels.add(entry.getValue());
                continue;
            }
            if (getRequestTimeout() > 0) {
                if (System.currentTimeMillis() - entry.getValue().getRequest().getCreateTime() > getRequestTimeout() * 1000L) {
                    entry.getValue().getResponse().renderCode(504);
                    socketChannels.add(entry.getValue());
                }
            }
        }
        return socketChannels;
    }

    private Set<SocketChannel> getClosedDecodedSocketSet() {
        Set<SocketChannel> removeHttpRequestList = new CopyOnWriteArraySet<>();
        for (Map.Entry<SocketChannel, HttpRequestDeCoder> entry : httpDeCoderMap.entrySet()) {
            if (!entry.getKey().isOpen()) {
                removeHttpRequestList.add(entry.getKey());
            }
        }
        return removeHttpRequestList;
    }

    private void clearRequestDecode(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socket : removeHttpRequestList) {
            httpDeCoderMap.remove(socket);
        }
    }

    public Map<SocketChannel, HttpRequestHandlerRunnable> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }
}
