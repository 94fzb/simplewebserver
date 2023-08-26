package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.SimpleWebServer;
import com.hibegin.http.server.api.HttpErrorHandle;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpDecodeRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpDecodeRunnable.class);

    private final ApplicationContext applicationContext;
    private final Map<Socket, LinkedBlockingDeque<RequestEvent>> socketRequestEventMap = new ConcurrentHashMap<>();
    private final SimpleWebServer simpleWebServer;
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ServerConfig serverConfig;
    private final Set<Socket> workingChannel = new CopyOnWriteArraySet<>();
    private final ReentrantLock lock = new ReentrantLock();

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
        lock.lock();
        try {
            List<Socket> needRemoveChannel = new CopyOnWriteArrayList<>();
            for (final Map.Entry<Socket, LinkedBlockingDeque<RequestEvent>> entry : socketRequestEventMap.entrySet()) {
                final Socket socket = entry.getKey();
                if (entry.getKey().isClosed()) {
                    needRemoveChannel.add(socket);
                    continue;
                }
                if (workingChannel.contains(socket)) {
                    continue;
                }
                final LinkedBlockingDeque<RequestEvent> blockingQueue = entry.getValue();
                final RequestEvent requestEvent = blockingQueue.poll();
                if (requestEvent == null) {
                    continue;
                }
                workingChannel.add(socket);
                serverConfig.getDecodeExecutor().execute(() -> {
                    try {
                        doParseHttpMessage(requestEvent, socket);
                    } finally {
                        workingChannel.remove(socket);
                        HttpDecodeRunnable.this.run();
                    }
                });
            }

            for (Socket socket : needRemoveChannel) {
                LinkedBlockingDeque<RequestEvent> entry = socketRequestEventMap.get(socket);
                if (entry == null) {
                    continue;
                }
                while (!entry.isEmpty()) {
                    entry.poll().deleteFile();
                }
                socketRequestEventMap.remove(socket);
                workingChannel.remove(socket);
            }
        } finally {
            lock.unlock();
        }
    }

    private void doParseHttpMessage(RequestEvent requestEvent, Socket socket) {
        SelectionKey key = requestEvent.getSelectionKey();
        Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = applicationContext.getHttpDeCoderMap().get(socket);
        try {
            if (Objects.isNull(codecEntry)) {
                return;
            }

            Map.Entry<Boolean, ByteBuffer> booleanEntry = codecEntry.getKey().doDecode(ByteBuffer.wrap(requestEvent.getRequestBytes()));
            if (booleanEntry.getValue().limit() > 0) {
                addBytesToQueue(key, socket, booleanEntry.getValue().array(), true);
            }
            if (Objects.equals(booleanEntry.getKey(), false)) {
                return;
            }
            if (serverConfig.isSupportHttp2()) {
                renderUpgradeHttp2Response(codecEntry.getValue());
            } else {
                httpRequestHandlerRunnableBlockingQueue.add(new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()));
                if (codecEntry.getKey().getRequest().getMethod() != HttpMethod.CONNECT) {
                    HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, codecEntry.getKey().getRequest().getHandler());
                    codecEntry = new AbstractMap.SimpleEntry<>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
                    applicationContext.getHttpDeCoderMap().put(socket, codecEntry);
                }
            }
        } catch (IOException e) {
            handleException(key, codecEntry.getKey(), null, 499, e);
        } catch (UnSupportMethodException e) {
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 400, e);
        } catch (RequestBodyTooLargeException e) {
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 413, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            handleException(key, codecEntry.getKey(), new HttpRequestHandlerRunnable(codecEntry.getKey().getRequest(), codecEntry.getValue()), 500, e);
        } finally {
            requestEvent.deleteFile();
        }
    }

    public HttpRequestHandlerRunnable getHttpRequestHandlerRunnable() {
        try {
            return httpRequestHandlerRunnableBlockingQueue.take();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return null;
    }


    private void addBytesToQueue(SelectionKey key, Socket socket, byte[] bytes, boolean toFirst) throws IOException {
        LinkedBlockingDeque<RequestEvent> entryBlockingQueue = socketRequestEventMap.get(socket);
        if (entryBlockingQueue == null) {
            entryBlockingQueue = new LinkedBlockingDeque<>();
            socketRequestEventMap.put(socket, entryBlockingQueue);
        }
        long newBufferSize = entryBlockingQueue.stream().mapToLong(RequestEvent::getLength).sum() + bytes.length;
        RequestEvent requestEvent;
        if (newBufferSize > requestConfig.getRequestMaxBufferSize()) {
            requestEvent = new RequestEvent(key, FileCacheKit.generatorRequestTempFile(serverConfig.getPort() +"", bytes));
        } else {
            requestEvent = new RequestEvent(key, bytes);
        }
        if (toFirst) {
            entryBlockingQueue.addFirst(requestEvent);
        } else {
            entryBlockingQueue.add(requestEvent);
        }
    }

    public void doRead(SocketChannel channel, SelectionKey key) throws IOException {
        if (Objects.isNull(channel) || !channel.isOpen()) {
            return;
        }

        Map.Entry<HttpRequestDeCoder, HttpResponse> codecEntry = applicationContext.getHttpDeCoderMap().get(channel.socket());
        ReadWriteSelectorHandler handler;
        if (codecEntry == null) {
            handler = simpleWebServer.getReadWriteSelectorHandlerInstance(channel, key);
            HttpRequestDeCoder requestDeCoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, handler);
            codecEntry = new AbstractMap.SimpleEntry<>(requestDeCoder, new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
            applicationContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
        } else {
            handler = codecEntry.getKey().getRequest().getHandler();
        }
        addBytesToQueue(key, channel.socket(), handler.handleRead().array(), false);
        this.run();
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

    private void handleException(SelectionKey key, HttpRequestDeCoder codec, HttpRequestHandlerRunnable httpRequestHandlerRunnable, int errorCode, Throwable throwable) {
        try {
            if (httpRequestHandlerRunnable != null && codec != null && codec.getRequest() != null) {
                if (!httpRequestHandlerRunnable.getRequest().getHandler().getChannel().socket().isClosed()) {
                    HttpErrorHandle errorHandle = serverConfig.getErrorHandle(errorCode);
                    if (Objects.nonNull(errorHandle)) {
                        errorHandle.doHandle(codec.getRequest(), httpRequestHandlerRunnable.getResponse(), throwable);
                    } else {
                        httpRequestHandlerRunnable.getResponse().renderCode(errorCode);
                    }
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
