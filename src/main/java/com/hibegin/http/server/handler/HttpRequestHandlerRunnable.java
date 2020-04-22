package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.impl.SimpleHttpRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandlerRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandlerRunnable.class);

    private final HttpRequest request;

    private final HttpResponse response;

    HttpRequestHandlerRunnable(HttpRequest request, HttpResponse response) {
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
            for (Interceptor interceptor : request.getApplicationContext().getInterceptors()) {
                if (!interceptor.doInterceptor(request, response)) {
                    break;
                }
            }
        } catch (Exception e) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                byteArrayOutputStream.write(LoggerUtil.recordStackTraceMsg(e).getBytes());
            } catch (IOException e1) {
                //e1.printStackTrace();
            }
            LOGGER.log(Level.SEVERE, "dispose error ", e);
            response.write(byteArrayOutputStream, 500);
        } finally {
            if (request.getMethod() != HttpMethod.CONNECT) {
                String responseConnection = response.getHeader().get("Connection");
                boolean keepAlive = !"close".equalsIgnoreCase(responseConnection);
                if (!keepAlive) {
                    // 渲染错误页面
                    if (!getSocket().isClosed()) {
                        LOGGER.log(Level.WARNING, "forget close stream " + getSocket().toString());
                        response.renderCode(404);
                    }
                    close();
                }
            }
            //System.out.println("(System.nanoTime() - start) = " + (System.nanoTime() - request.getCreateTime()));
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void close() {
        if (request instanceof SimpleHttpRequest) {
            ((SimpleHttpRequest) request).deleteTempUploadFiles();
        }
        for (HttpRequestListener requestListener : request.getApplicationContext().getServerConfig().getHttpRequestListenerList()) {
            requestListener.destroy(getRequest(), getResponse());
        }
    }
}
