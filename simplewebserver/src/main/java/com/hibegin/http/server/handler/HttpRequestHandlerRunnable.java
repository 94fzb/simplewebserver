package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.*;
import com.hibegin.http.server.impl.SimpleHttpRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestHandlerRunnable implements Runnable {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestHandlerRunnable.class);

    private final HttpRequest request;

    private final HttpResponse response;

    public HttpRequestHandlerRunnable(HttpRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }

    protected boolean isOpen() {
        if (Objects.isNull(request.getHandler())) {
            return false;
        }
        return request.getHandler().getChannel().isOpen();
    }

    @Override
    public void run() {
        try {
            for (HttpRequestListener httpRequestListener : request.getServerConfig().getHttpRequestListenerList()) {
                httpRequestListener.onCreate(request, response);
            }
            for (Interceptor interceptor : request.getApplicationContext().getInterceptors()) {
                if (interceptor instanceof HandleAbleInterceptor) {
                    if (!((HandleAbleInterceptor) interceptor).isHandleAble(request)) {
                        continue;
                    }
                }
                if (!interceptor.doInterceptor(request, response)) {
                    break;
                }
            }
        } catch (Exception e) {
            HttpErrorHandle errorHandle = request.getServerConfig().getErrorHandle(500);
            if (Objects.nonNull(errorHandle)) {
                if (e instanceof InvocationTargetException) {
                    errorHandle.doHandle(request, response, ((InvocationTargetException) e).getTargetException());
                } else {
                    errorHandle.doHandle(request, response, e);
                }
                return;
            }
            defaultErrorResponse(e);
        } finally {
            request.getServerConfig().getHttpRequestListenerList().forEach(httpRequestListener -> {
                httpRequestListener.onHandled(request, response);
            });
            if (request.getMethod() == HttpMethod.CONNECT) {
                //ignore
            } else {
                String responseConnection = response.getHeader().get("Connection");
                boolean needClose = "close".equalsIgnoreCase(responseConnection);
                if (needClose) {
                    // 渲染错误页面
                    if (isOpen()) {
                        LOGGER.log(Level.WARNING, request.getUri() + " forget close stream");
                        response.renderCode(404);
                    }
                    close();
                } else {
                    if (request instanceof SimpleHttpRequest) {
                        ((SimpleHttpRequest) request).deleteTempUploadFiles();
                    }
                }
            }
            //System.out.println("(System.nanoTime() - start) = " + (System.nanoTime() - request.getCreateTime()));
        }
    }

    private void defaultErrorResponse(Exception e) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(LoggerUtil.recordStackTraceMsg(e).getBytes());
        } catch (IOException e1) {
            //e1.printStackTrace();
        }
        LOGGER.log(Level.SEVERE, "dispose error ", e);
        response.write(byteArrayOutputStream, 500);
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
            requestListener.onDestroy(getRequest(), getResponse());
        }
    }
}
