package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.ReadWriteSelectorHandlerBuilder;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.PlainRequestToSslPortException;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.HttpRequestBuilder;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class HttpRequestMessageHandler {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestMessageHandler.class);

    private final ApplicationContext applicationContext;
    private final Map<SocketChannel, Queue<String>> socketRequestEventMap = new ConcurrentHashMap<>();
    private final ReadWriteSelectorHandlerBuilder readWriteSelectorHandlerBuilder;
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ServerConfig serverConfig;
    private final Set<SocketChannel> workingChannel = new CopyOnWriteArraySet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private static final AtomicLong FILE_ID = new AtomicLong();

    public HttpRequestMessageHandler(ApplicationContext applicationContext, ReadWriteSelectorHandlerBuilder readWriteSelectorHandlerBuilder,
                                     RequestConfig requestConfig, ResponseConfig responseConfig) {
        this.applicationContext = applicationContext;
        this.readWriteSelectorHandlerBuilder = readWriteSelectorHandlerBuilder;
        this.requestConfig = requestConfig;
        this.responseConfig = responseConfig;
        this.serverConfig = applicationContext.getServerConfig();
    }

    private void run() {
        lock.lock();
        try {
            List<SocketChannel> needRemoveChannel = new CopyOnWriteArrayList<>();
            for (final Map.Entry<SocketChannel, Queue<String>> entry : socketRequestEventMap.entrySet()) {
                final SocketChannel socketChannel = entry.getKey();
                if (!socketChannel.isOpen()) {
                    needRemoveChannel.add(socketChannel);
                    continue;
                }
                if (workingChannel.contains(socketChannel)) {
                    continue;
                }
                final Queue<String> blockingQueue = entry.getValue();
                String eventKey = blockingQueue.poll();
                if (Objects.isNull(eventKey)) {
                    continue;
                }
                workingChannel.add(socketChannel);
                Runnable parseHttpMessageRunnable = new ParseHttpMessageRunnable(eventKey, applicationContext, responseConfig, socketChannel) {
                    @Override
                    public void addBytesToQueue(SelectionKey key, SocketChannel socket, byte[] bytes, boolean toFirst) throws Exception {
                        HttpRequestMessageHandler.this.addBytesToQueue(key, socket, bytes, toFirst);
                    }

                    @Override
                    public void onDone() {
                        workingChannel.remove(socketChannel);
                        HttpRequestMessageHandler.this.run();
                    }
                };
                serverConfig.getDecodeExecutor().execute(parseHttpMessageRunnable);
            }

            for (SocketChannel socket : needRemoveChannel) {
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

    public static void main(String[] args) {
        Map<String, String> m = new HashMap<>();
        String a = m.computeIfAbsent("a", (k) -> "2");
        System.out.println("m = " + m);
        System.out.println("a = " + a);
    }

    private static String generatorFileName(long port) {
        return FileCacheKit.SERVER_WEB_SERVER_TEMP_FILE_PREFIX + "req-event-" + System.currentTimeMillis() + "-" + FILE_ID.incrementAndGet() + FileCacheKit.suffix(port + "");
    }

    public void addBytesToQueue(SelectionKey key, SocketChannel socket, byte[] bytes, boolean toFirst) throws Exception {
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
            HttpRequestDeCoder codecEntry = applicationContext.getHttpDeCoderMap().get(channel);
            if (Objects.isNull(codecEntry)) {
                codecEntry = new HttpRequestDecoderImpl(requestConfig, applicationContext, readWriteSelectorHandlerBuilder.getReadWriteSelectorHandlerInstance(channel));
                applicationContext.getHttpDeCoderMap().put(channel, codecEntry);
            }
            byte[] data = codecEntry.getHandler().handleRead().array();
            if (data.length == 0) {
                return;
            }
            addBytesToQueue(key, channel, data, false);
        } catch (PlainRequestToSslPortException e) {
            HttpRequest httpRequest = HttpRequestBuilder.buildRequest(HttpMethod.GET, "/", "127.0.0.1", "", requestConfig, applicationContext);
            HttpExceptionUtils.handleException(serverConfig, new HttpRequestHandlerRunnable(httpRequest, new SimpleHttpResponse(httpRequest, responseConfig)), 400, e);
        } finally {
            this.run();
        }
    }

}
