package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpRequest;

import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandlerThread extends Thread {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandlerThread.class);

    private HttpRequest request;
    private ServerContext serverContext;

    private HttpResponse response;
    private Socket socket;
    private volatile boolean interrupted;

    public HttpRequestHandlerThread(HttpRequest request, HttpResponse response, ServerContext serverContext) {
        this.serverContext = serverContext;
        this.request = request;
        this.response = response;
        this.socket = request.getHandler().getChannel().socket();
    }

    @Override
    public void run() {
        try {
            if (!serverContext.getServerConfig().getHttpRequestListenerList().isEmpty()) {
                for (HttpRequestListener httpRequestListener : serverContext.getServerConfig().getHttpRequestListenerList()) {
                    httpRequestListener.create(request, response);
                }
            }
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
                    // 渲染错误页面
                    if (!socket.isClosed()) {
                        LOGGER.log(Level.WARNING, "forget close stream " + socket.toString());
                        response.renderCode(404);
                    }
                }
                serverContext.getHttpDeCoderMap().remove(socket);
                close();
            }
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        if (!interrupted) {
            close();
        }
    }

    private void close() {
        interrupted = true;
        new Thread() {
            @Override
            public void run() {
                //LOGGER.info(request.getMethod() + ": " + request.getUrl() + " " + (System.currentTimeMillis() - request.getCreateTime()) + " ms");
                if (socket.isClosed()) {
                    serverContext.getHttpDeCoderMap().remove(socket);
                }
                if (request instanceof SimpleHttpRequest) {
                    ((SimpleHttpRequest) request).deleteTempUploadFiles();
                }
                for (HttpRequestListener requestListener : serverContext.getServerConfig().getHttpRequestListenerList()) {
                    requestListener.destroy(getRequest(), getResponse());
                }
            }
        }.start();
    }
}
