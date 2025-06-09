package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.SimpleWebServer;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequest;
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
import com.hibegin.http.server.util.HttpRequestBuilder;
import com.hibegin.http.server.util.StatusCodeUtil;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpDecodeRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpDecodeRunnable.class);

    private final ApplicationContext applicationContext;
    private final Map<Socket, Queue<String>> socketRequestEventMap = new ConcurrentHashMap<>();
    private final SimpleWebServer simpleWebServer;
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ServerConfig serverConfig;
    private final Set<Socket> workingChannel = new CopyOnWriteArraySet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final CheckRequestRunnable checkRequestRunnable;
    private static final AtomicLong FILE_ID = new AtomicLong();

    public HttpDecodeRunnable(ApplicationContext applicationContext, SimpleWebServer simpleWebServer, RequestConfig requestConfig, ResponseConfig responseConfig, CheckRequestRunnable runnable) {
        this.applicationContext = applicationContext;
        this.simpleWebServer = simpleWebServer;
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        this.serverConfig = applicationContext.getServerConfig();
        this.checkRequestRunnable = runnable;
    }

    @Override
    public void run() {
        lock.lock();
        try {
            List<Socket> needRemoveChannel = new CopyOnWriteArrayList<>();
            for (final Map.Entry<Socket, Queue<String>> entry : socketRequestEventMap.entrySet()) {
                final Socket socket = entry.getKey();
                if (entry.getKey().isClosed()) {
                    needRemoveChannel.add(socket);
                    continue;
                }
                if (workingChannel.contains(socket)) {
                    continue;
                }
                final Queue<String> blockingQueue = entry.getValue();
                String key = blockingQueue.poll();
                if (Objects.isNull(key)) {
                    continue;
                }
                final RequestEvent requestEvent = serverConfig.getHybridStorage().get(key);
                if (requestEvent == null) {
                    continue;
                }
                workingChannel.add(socket);
                serverConfig.getDecodeExecutor().execute(() -> {
                    try {
                        doParseHttpMessage(requestEvent, socket);
                    } finally {
                        serverConfig.getHybridStorage().remove(key);
                        workingChannel.remove(socket);
                        HttpDecodeRunnable.this.run();
                    }
                });
            }

            for (Socket socket : needRemoveChannel) {
                Queue<String> entry = socketRequestEventMap.get(socket);
                if (entry == null) {
                    continue;
                }
                while (!entry.isEmpty()) {
                    serverConfig.getHybridStorage().remove(entry.poll());
                }
                socketRequestEventMap.remove(socket);
                workingChannel.remove(socket);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    private void requestExec(HttpRequestHandlerRunnable httpRequestHandlerRunnable) {
        if (Objects.isNull(httpRequestHandlerRunnable)) {
            return;
        }
        SocketChannel socket = httpRequestHandlerRunnable.getRequest().getHandler().getChannel();
        if (Objects.equals(httpRequestHandlerRunnable.getRequest().getMethod(), HttpMethod.CONNECT)) {
            HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
            if (oldHttpRequestHandlerRunnable == null) {
                checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
                serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
            }
            return;
        }
        HttpRequestHandlerRunnable oldHttpRequestHandlerRunnable = checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().get(socket);
        //清除老的请求
        if (oldHttpRequestHandlerRunnable != null) {
            oldHttpRequestHandlerRunnable.close();
        }
        checkRequestRunnable.getChannelHttpRequestHandlerThreadMap().put(socket, httpRequestHandlerRunnable);
        serverConfig.getRequestExecutor().execute(httpRequestHandlerRunnable);
    }

    private void doParseHttpMessage(RequestEvent requestEvent, Socket socket) {
        SelectionKey key = requestEvent.getSelectionKey();
        HttpRequestDeCoder requestDeCoder = applicationContext.getHttpDeCoderMap().get(socket);
        try {
            if (Objects.isNull(requestDeCoder)) {
                return;
            }

            Map.Entry<Boolean, ByteBuffer> booleanEntry = requestDeCoder.doDecode(ByteBuffer.wrap(requestEvent.getRequestBody()));
            if (booleanEntry.getValue().limit() > 0) {
                addBytesToQueue(key, socket, booleanEntry.getValue().array(), true);
            }
            if (Objects.equals(booleanEntry.getKey(), false)) {
                return;
            }
            if (serverConfig.isSupportHttp2()) {
                renderUpgradeHttp2Response(new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig));
            } else {
                requestExec(new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)));
                if (requestDeCoder.getRequest().getMethod() != HttpMethod.CONNECT) {
                    requestDeCoder.doNext();
                }
            }
        } catch (IOException e) {
            handleException(key, requestDeCoder, null, 499, e);
        } catch (UnSupportMethodException e) {
            handleException(key, requestDeCoder, new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)), 400, e);
        } catch (RequestBodyTooLargeException e) {
            handleException(key, requestDeCoder, new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)), 413, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            handleException(key, requestDeCoder, new HttpRequestHandlerRunnable(requestDeCoder.getRequest(), new SimpleHttpResponse(requestDeCoder.getRequest(), responseConfig)), 500, e);
        }
    }

    public static void main(String[] args) {
        Map<String, String> m = new HashMap<>();
        String a = m.computeIfAbsent("a", (k) -> "2");
        System.out.println("m = " + m);
        System.out.println("a = " + a);
    }

    private static String generatorFileName(long port) {
        return FileCacheKit.SERVER_WEB_SERVER_TEMP_FILE_PREFIX + "req-event-" + System.currentTimeMillis() + "-" + FILE_ID.incrementAndGet() + FileCacheKit.suffix(port + "");
    }

    private void addBytesToQueue(SelectionKey key, Socket socket, byte[] bytes, boolean toFirst) throws Exception {
        if (Objects.isNull(bytes) || bytes.length == 0) {
            return;
        }
        LinkedBlockingDeque<String> entryBlockingQueue = (LinkedBlockingDeque<String>) socketRequestEventMap.computeIfAbsent(socket, (k) -> new LinkedBlockingDeque<>());
        long newBufferSize = entryBlockingQueue.stream().mapToLong((e) -> {
            return serverConfig.getHybridStorage().getLengthByKey(e);
        }).sum() + bytes.length;
        boolean toDisk = newBufferSize > requestConfig.getRequestMaxBufferSize();
        RequestEventStorable requestEventStorable = new RequestEventStorable(key, bytes, generatorFileName(serverConfig.getPort()));
        String cacheKey = toDisk ? serverConfig.getHybridStorage().putToDisk(requestEventStorable) : serverConfig.getHybridStorage().put(requestEventStorable);
        if (toFirst) {
            entryBlockingQueue.addFirst(cacheKey);
        } else {
            entryBlockingQueue.add(cacheKey);
        }
    }

    public void doRead(SocketChannel channel, SelectionKey key) throws Exception {
        if (Objects.isNull(channel) || !channel.isOpen()) {
            return;
        }
        try {
            HttpRequestDeCoder codecEntry = applicationContext.getHttpDeCoderMap().get(channel.socket());
            if (Objects.isNull(codecEntry)) {
                codecEntry = new HttpRequestDecoderImpl(requestConfig, applicationContext, simpleWebServer.getReadWriteSelectorHandlerInstance(channel, key));
                applicationContext.getHttpDeCoderMap().put(channel.socket(), codecEntry);
            }
            try {
                byte[] data = codecEntry.getHandler().handleRead().array();
                if (data.length == 0) {
                    return;
                }
                addBytesToQueue(key, channel.socket(), data, false);
            } catch (SSLException e) {
                HttpRequest httpRequest = HttpRequestBuilder.buildRequest(HttpMethod.GET, "/", "127.0.0.1", "", requestConfig, applicationContext);
                handleException(key, codecEntry, new HttpRequestHandlerRunnable(httpRequest, new SimpleHttpResponse(httpRequest, responseConfig)), 400, e);
            }
        } finally {
            this.run();
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
