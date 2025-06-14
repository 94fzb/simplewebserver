package com.hibegin.http.server.handler;

import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ServerConfig;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class CheckRequestRunnable implements Runnable {

    private final Map<SocketChannel, HttpRequestHandlerRunnable> channelHttpRequestHandlerThreadMap;
    private final ServerConfig serverConfig;
    private final Map<Socket, HttpRequestDeCoder> httpDeCoderMap;


    public CheckRequestRunnable(ServerConfig serverConfig, Map<Socket, HttpRequestDeCoder> httpDeCoderMap) {
        this.channelHttpRequestHandlerThreadMap = new ConcurrentHashMap<>();
        this.serverConfig  = serverConfig;
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

    private void clearRequestListener(Set<SocketChannel> removeHttpRequestList) {
        for (SocketChannel socketChannel : removeHttpRequestList) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                //ignore
            }
            if (channelHttpRequestHandlerThreadMap.get(socketChannel) != null) {
                channelHttpRequestHandlerThreadMap.get(socketChannel).close();
                channelHttpRequestHandlerThreadMap.remove(socketChannel);
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
        for (Map.Entry<Socket, HttpRequestDeCoder> entry : httpDeCoderMap.entrySet()) {
            Socket socket = entry.getKey();
            if (socket.isClosed() || !socket.isConnected()) {
                removeHttpRequestList.add(socket);
            }
        }
        return removeHttpRequestList;
    }

    private void clearRequestDecode(Set<Socket> removeHttpRequestList) {
        for (Socket socket : removeHttpRequestList) {
            httpDeCoderMap.remove(socket);
        }
    }

    public Map<SocketChannel, HttpRequestHandlerRunnable> getChannelHttpRequestHandlerThreadMap() {
        return channelHttpRequestHandlerThreadMap;
    }
}
