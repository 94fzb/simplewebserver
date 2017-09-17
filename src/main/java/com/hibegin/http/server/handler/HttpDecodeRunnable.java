package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.SimpleWebServer;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.FrameUtil;
import com.hibegin.http.server.util.StatusCodeUtil;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpDecodeRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpDecodeRunnable.class);

    private ServerContext serverContext;
    private Map<SocketChannel, BlockingQueue<Map.Entry<SelectionKey, byte[]>>> socketChannelBlockingQueueConcurrentHashMap = new ConcurrentHashMap<>();
    private SimpleWebServer simpleWebServer;
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private ServerConfig serverConfig;
    private Set<SocketChannel> workingChannel = new CopyOnWriteArraySet<>();

    private BlockingQueue<HttpRequestHandlerThread> httpRequestHandlerThreadBlockingQueue = new LinkedBlockingQueue<>();

    public HttpDecodeRunnable(ServerContext serverContext, SimpleWebServer simpleWebServer, RequestConfig requestConfig, ResponseConfig responseConfig, ServerConfig serverConfig) {
        this.serverContext = serverContext;
        this.simpleWebServer = simpleWebServer;
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        this.serverConfig = serverConfig;
    }

    @Override
    public void run() {
        List<SocketChannel> needRemoveChannel = new CopyOnWriteArrayList<>();
        for (final Map.Entry<SocketChannel, BlockingQueue<Map.Entry<SelectionKey, byte[]>>> entry : socketChannelBlockingQueueConcurrentHashMap.entrySet()) {
            final SocketChannel channel = entry.getKey();
            if (entry.getKey().socket().isClosed()) {
                needRemoveChannel.add(channel);
            } else {
                if (!workingChannel.contains(channel)) {
                    workingChannel.add(channel);
                    Thread thread = new Thread() {
                        @Override
                        public void run() {
                            BlockingQueue<Map.Entry<SelectionKey, byte[]>> blockingQueue = entry.getValue();
                            while (!blockingQueue.isEmpty()) {
                                Map.Entry<SelectionKey, byte[]> selectionKeyEntry = blockingQueue.poll();
                                SelectionKey key = selectionKeyEntry.getKey();
                                Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = serverContext.getHttpDeCoderMap().get(channel.socket());
                                if (codecEntry != null && key != null) {
                                    try {
                                        if (!channel.socket().isClosed()) {
                                            byte[] bytes = selectionKeyEntry.getValue();
                                            if (bytes.length > 0 && codecEntry.getKey().doDecode(bytes)) {
                                                if (serverConfig.isSupportHttp2()) {
                                                    renderUpgradeHttp2Response(codecEntry.getValue());
                                                } else {
                                                    httpRequestHandlerThreadBlockingQueue.add(new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext));
                                                    if (codecEntry.getKey().getRequest().getMethod() != HttpMethod.CONNECT) {
                                                        HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, serverContext, codecEntry.getKey().getRequest().getHandler());
                                                        codecEntry = new AbstractMap.SimpleEntry<HttpRequestDeCoder, HttpResponse>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                                                        serverContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (EOFException | ClosedChannelException e) {
                                        //do nothing
                                        handleException(key, codecEntry.getKey(), null, 400);
                                    } catch (UnSupportMethodException | IOException e) {
                                        LOGGER.log(Level.SEVERE, "", e);
                                        handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 400);
                                    } catch (ContentLengthTooLargeException e) {
                                        handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 413);
                                    } catch (Exception e) {
                                        handleException(key, codecEntry.getKey(), new HttpRequestHandlerThread(codecEntry.getKey().getRequest(), codecEntry.getValue(), serverContext), 500);
                                        LOGGER.log(Level.SEVERE, "", e);
                                    }
                                }
                            }
                            workingChannel.remove(channel);
                        }
                    };
                    serverConfig.getDecodeExecutor().execute(thread);
                }
            }
        }
        for (SocketChannel socketChannel : needRemoveChannel) {
            socketChannelBlockingQueueConcurrentHashMap.remove(socketChannel);
            workingChannel.remove(socketChannel);
        }
    }

    public HttpRequestHandlerThread getHttpRequestHandlerThread() {
        return httpRequestHandlerThreadBlockingQueue.poll();
    }

    public void addTask(SocketChannel channel, SelectionKey key) {
        if (channel != null && channel.isOpen()) {
            Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = serverContext.getHttpDeCoderMap().get(channel.socket());
            ReadWriteSelectorHandler handler = null;
            if (codecEntry == null) {
                try {
                    handler = simpleWebServer.getReadWriteSelectorHandlerInstance(channel, key);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "", e);
                }
                HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, serverContext, handler);
                codecEntry = new AbstractMap.SimpleEntry<HttpRequestDeCoder, HttpResponse>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                serverContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
            } else {
                handler = codecEntry.getKey().getRequest().getHandler();
            }
            if (handler != null && !channel.socket().isClosed()) {
                try {
                    byte[] bytes = handler.handleRead().array();
                    if (bytes.length > 0) {
                        BlockingQueue<Map.Entry<SelectionKey, byte[]>> entryBlockingQueue = socketChannelBlockingQueueConcurrentHashMap.get(channel);
                        if (entryBlockingQueue == null) {
                            entryBlockingQueue = new LinkedBlockingQueue<>();
                            socketChannelBlockingQueueConcurrentHashMap.put(channel, entryBlockingQueue);
                        }
                        entryBlockingQueue.add(new AbstractMap.SimpleEntry<>(key, bytes));
                    }
                } catch (EOFException | SocketException e) {
                    //ignore
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "", e);
                }
            }
        }
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

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, HttpRequestHandlerThread httpRequestHandlerThread, int errorCode) {
        try {
            if (httpRequestHandlerThread != null && codec != null && codec.getRequest() != null) {
                if (!httpRequestHandlerThread.getRequest().getHandler().getChannel().socket().isClosed()) {
                    httpRequestHandlerThread.getResponse().renderCode(errorCode);
                }
                httpRequestHandlerThread.interrupt();
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
}
