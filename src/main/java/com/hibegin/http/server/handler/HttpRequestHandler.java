package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.*;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.HttpMethod;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpResponse;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandler extends Thread {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandler.class);

    private HttpRequest request;
    private ServerContext serverContext;

    private HttpResponse response;
    private SocketChannel channel;

    public HttpRequestHandler(HttpRequestDeCoder codec, ResponseConfig responseConfig, ServerContext serverContext) {
        this.serverContext = serverContext;
        this.request = codec.getRequest();
        this.response = new SimpleHttpResponse(codec.getRequest(), responseConfig);
        if(!serverContext.getServerConfig().getHttpRequestListenerList().isEmpty()){
            for(HttpRequestListener httpRequestListener:serverContext.getServerConfig().getHttpRequestListenerList()){
                httpRequestListener.create(request,response);
            }
        }
        this.channel = codec.getRequest().getHandler().getChannel();
    }

    @Override
    public void run() {
        try {
            for (Interceptor interceptor : serverContext.getInterceptors()) {
                if (!interceptor.doInterceptor(request, response)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "dispose error ", e);
        } finally {
            if (request.getMethod() != HttpMethod.CONNECT) {
                boolean keepAlive = request.getHeader("Connection") != null && "keep-alive".equalsIgnoreCase(request.getHeader("Connection"));
                if (keepAlive) {
                    keepAlive = response.getHeader().get("Connection") != null && !"close".equalsIgnoreCase(response.getHeader().get("Connection"));
                }
                if (!keepAlive) {
                    Socket socket = channel.socket();
                    // 渲染错误页面
                    if (!socket.isClosed()) {
                        LOGGER.log(Level.WARNING, "forget close stream " + socket.toString());
                        response.renderCode(404);
                    }
                }
                LOGGER.info(request.getUrl() + " " + (System.currentTimeMillis() - request.getCreateTime()) + " ms");
                serverContext.getHttpDeCoderMap().remove(channel);
            }
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }
}
