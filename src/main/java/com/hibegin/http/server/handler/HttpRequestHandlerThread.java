package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.ServerContext;
import com.hibegin.http.server.impl.SimpleHttpRequest;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandlerThread extends Thread {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandlerThread.class);

    private HttpRequest request;

    private HttpResponse response;
    private volatile boolean interrupted;

    public HttpRequestHandlerThread(HttpRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }

    private Socket getSocket() {
        return request.getHandler().getChannel().socket();
    }

    @Override
    public void run() {
        try {
            for (HttpRequestListener httpRequestListener : request.getServerConfig().getHttpRequestListenerList()) {
                httpRequestListener.create(request, response);
            }
            for (Interceptor interceptor : request.getServerContext().getInterceptors()) {
                if (!interceptor.doInterceptor(request, response)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "dispose error ", e);
        } finally {
            if (request.getMethod() != HttpMethod.CONNECT) {
                String requestConnection = request.getHeader("Connection");
                String responseConnection = response.getHeader().get("Connection");
                boolean keepAlive = requestConnection != null && "keep-alive".equalsIgnoreCase(requestConnection);
                if (keepAlive) {
                    keepAlive = responseConnection != null && !"close".equalsIgnoreCase(responseConnection);
                }
                if (!keepAlive) {
                    // 渲染错误页面
                    if (!getSocket().isClosed()) {
                        LOGGER.log(Level.WARNING, "forget close stream " + getSocket().toString());
                        response.renderCode(404);
                    }
                    close();
                    request.getServerContext().getHttpDeCoderMap().remove(getSocket());
                }
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
        //LOGGER.info(request.gestMethod() + ": " + request.getUrl() + " " + (System.currentTimeMillis() - request.getCreateTime()) + " ms");
        if (getSocket().isClosed()) {
            request.getServerContext().getHttpDeCoderMap().remove(getSocket());
        }
        if (request instanceof SimpleHttpRequest) {
            ((SimpleHttpRequest) request).deleteTempUploadFiles();
        }
        for (HttpRequestListener requestListener : request.getServerContext().getServerConfig().getHttpRequestListenerList()) {
            requestListener.destroy(getRequest(), getResponse());
        }
    }
}
