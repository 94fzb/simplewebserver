package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpResponse;

import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandler extends Thread {

    private static Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandler.class);

    private SelectionKey key;
    private HttpRequest request;
    private ServerConfig serverConfig;
    private ServerContext serverContext;

    private SimpleHttpResponse response;
    private Socket socket;

    public HttpRequestHandler(HttpRequestDeCoder codec, SelectionKey key, ServerConfig serverConfig, ResponseConfig responseConfig, ServerContext serverContext) {
        this.key = key;
        this.serverContext = serverContext;
        this.request = codec.getRequest();
        this.serverConfig = serverConfig;

        this.response = new SimpleHttpResponse(codec.getRequest(), responseConfig);
        this.socket = ((SocketChannel) key.channel()).socket();
    }

    @Override
    public void run() {
        //timeout
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                        if (socket.isClosed()) {
                            break;
                        }
                        if (System.currentTimeMillis() - request.getCreateTime() > serverConfig.getTimeOut() * 1000) {
                            response.renderCode(504);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        try {
            List<Class<Interceptor>> interceptors = serverConfig.getInterceptors();
            for (Class<Interceptor> interceptor : interceptors) {
                if (!interceptor.newInstance().doInterceptor(request, response)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "dispose error ", e);
        } finally {
            boolean keepAlive = request.getHeader("Connection") != null && "keep-alive".equalsIgnoreCase(request.getHeader("Connection"));
            if (keepAlive) {
                keepAlive = response.getHeader().get("Connection") != null && !"close".equalsIgnoreCase(response.getHeader().get("Connection"));
            }
            if (!keepAlive) {
                // 渲染错误页面
                if (!socket.isClosed()) {
                    LOGGER.log(Level.WARNING, "forget close stream " + socket.toString());
                    response.renderCode(404);
                }
            }
            LOGGER.info(request.getUrl() + " " + (System.currentTimeMillis() - request.getCreateTime()) + " ms");
            serverContext.getHttpDeCoderMap().remove(key.channel());
        }
    }
}
