package com.hibegin.http.server.handler;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.SimpleWebServer;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.RequestBodyTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.FrameUtil;
import com.hibegin.http.server.util.StatusCodeUtil;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpDecodeRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpDecodeRunnable.class);

    private final ApplicationContext applicationContext;
    private final Map<SocketChannel, LinkedBlockingDeque<RequestEvent>> socketChannelBlockingQueueConcurrentHashMap = new ConcurrentHashMap<>();
    private final SimpleWebServer simpleWebServer;
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ServerConfig serverConfig;
    private final Set<SocketChannel> workingChannel = new CopyOnWriteArraySet<>();

    private final BlockingQueue<HttpRequestHandlerRunnable> httpRequestHandlerRunnableBlockingQueue = new LinkedBlockingQueue<>();

    public HttpDecodeRunnable(ApplicationContext applicationContext, SimpleWebServer simpleWebServer, RequestConfig requestConfig, ResponseConfig responseConfig) {
        this.applicationContext = applicationContext;
        this.simpleWebServer = simpleWebServer;
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        this.serverConfig = applicationContext.getServerConfig();
    }

    @Override
    public void run() {
        List<SocketChannel> needRemoveChannel = new CopyOnWriteArrayList<>();
        for (final Map.Entry<SocketChannel, LinkedBlockingDeque<RequestEvent>> entry : socketChannelBlockingQueueConcurrentHashMap.entrySet()) {
            final SocketChannel channel = entry.getKey();
            if (entry.getKey().socket().isClosed()) {
                needRemoveChannel.add(channel);
                continue;
            }
            if (workingChannel.contains(channel)) {
                continue;
            }
            final LinkedBlockingDeque<RequestEvent> blockingQueue = entry.getValue();
            final RequestEvent requestEvent = blockingQueue.poll();
            if (requestEvent == null) {
                continue;
            }
            workingChannel.add(channel);
            serverConfig.getDecodeExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        doParseHttpMessage(requestEvent, channel.socket(), blockingQueue);
                    } finally {
                        workingChannel.remove(channel);
                    }
                }
            });
        }

        for (SocketChannel socketChannel : needRemoveChannel) {
            LinkedBlockingDeque<RequestEvent> entry = socketChannelBlockingQueueConcurrentHashMap.get(socketChannel);
            if (entry == null) {
                continue;
            }
            while (!entry.isEmpty()) {
                entry.poll().getFile().delete();
            }
            socketChannelBlockingQueueConcurrentHashMap.remove(socketChannel);
            workingChannel.remove(socketChannel);
        }
    }

    private void doParseHttpMessage(RequestEvent requestEvent, Socket socket, LinkedBlockingDeque<RequestEvent> blockingQueue) {
        SelectionKey key = requestEvent.getSelectionKey();
        Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = applicationContext.getHttpDeCoderMap().get(socket);
        File file = requestEvent.getFile();
        try {
            if (codecEntry != null) {
                Map.Entry<Boolean, ByteBuffer> booleanEntry = codecEntry.getKey().doDecode(ByteBuffer.wrap(IOUtil.getByteByInputStream(new FileInputStream(file))));
                if (booleanEntry.getKey()) {
                    if (booleanEntry.getValue().limit() > 0) {
                        blockingQueue.addFirst(new RequestEvent(key, FileCacheKit.generatorRequestTempFile(serverConfig.getPort(), booleanEntry.getValue().array())));
                    }
                    if (serverConfig.isSupportHttp2()) {
                        renderUpgradeHttp2Response(codecEntry.getValue());
                    } else {
                        httpRequestHandlerRunnableBlockingQueue.add(new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()));
                        if (codecEntry.getKey().getRequest().getMethod() != HttpMethod.CONNECT) {
                            HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, codecEntry.getKey().getRequest().getHandler());
                            codecEntry = new AbstractMap.SimpleEntry<HttpRequestDeCoder, HttpResponse>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                            applicationContext.getHttpDeCoderMap().put(socket, codecEntry);
                        }
                    }
                }
            }
        } catch (EOFException | ClosedChannelException e) {
            //do nothing
            handleException(key, codecEntry.getKey(), null, 400);
        } catch (UnSupportMethodException | IOException e) {
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 400);
        } catch (RequestBodyTooLargeException e) {
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 413);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 500);
        } finally {
            file.delete();
        }
    }

    public HttpRequestHandlerRunnable getHttpRequestHandlerThread() {
        try {
            return httpRequestHandlerRunnableBlockingQueue.take();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return null;
    }

    public void doRead(SocketChannel channel, SelectionKey key) throws IOException {
        if (channel != null && channel.isOpen()) {
            Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = applicationContext.getHttpDeCoderMap().get(channel.socket());
            ReadWriteSelectorHandler handler;
            if (codecEntry == null) {
                handler = simpleWebServer.getReadWriteSelectorHandlerInstance(channel, key);
                HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, handler);
                codecEntry = new AbstractMap.SimpleEntry<HttpRequestDeCoder, HttpResponse>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                applicationContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
            } else {
                handler = codecEntry.getKey().getRequest().getHandler();
            }
            LinkedBlockingDeque<RequestEvent> entryBlockingQueue = socketChannelBlockingQueueConcurrentHashMap.get(channel);
            if (entryBlockingQueue == null) {
                entryBlockingQueue = new LinkedBlockingDeque<>();
                socketChannelBlockingQueueConcurrentHashMap.put(channel, entryBlockingQueue);
            }
            entryBlockingQueue.add(new RequestEvent(key, FileCacheKit.generatorRequestTempFile(serverConfig.getPort(), handler.handleRead().array())));
            synchronized (this) {
                this.notify();
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

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, HttpRequestHandlerRunnable httpRequestHandlerRunnable, int errorCode) {
        try {
            if (httpRequestHandlerRunnable != null && codec != null && codec.getRequest() != null) {
                if (!httpRequestHandlerRunnable.getRequest().getHandler().getChannel().socket().isClosed()) {
                    httpRequestHandlerRunnable.getResponse().renderCode(errorCode);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "error", e);
        } finally {
            try {
                key.cancel();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "error", e);
            }
            try {
                key.channel().close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "error", e);
            }
        }
    }
}
