package com.hibegin.http.server.handler;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.config.ServerConfig;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpExceptionUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpExceptionUtils.class);

    public static void handleException(ServerConfig serverConfig, HttpRequestHandlerRunnable httpRequestHandlerRunnable, int errorCode, Throwable throwable) {
        try {
            if (Objects.isNull(httpRequestHandlerRunnable)) {
                return;
            }
            if (httpRequestHandlerRunnable.getRequest().getHandler().getChannel().isOpen()) {
                HttpErrorHandle errorHandle = serverConfig.getErrorHandle(errorCode);
                if (Objects.nonNull(errorHandle)) {
                    errorHandle.doHandle(httpRequestHandlerRunnable.getRequest(), httpRequestHandlerRunnable.getResponse(), throwable);
                } else {
                    httpRequestHandlerRunnable.getResponse().renderCode(errorCode);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "error", e);
        }
    }
}
