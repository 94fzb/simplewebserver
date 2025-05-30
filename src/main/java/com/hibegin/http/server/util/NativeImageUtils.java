package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.LocalFileStaticResourceLoader;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.web.MethodInterceptor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Logger;

public class NativeImageUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(NativeImageUtils.class);

    public static void doLoopResourceLoad(File[] files, String basePath, String uriStart) {
        if (Objects.isNull(files)) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                doLoopResourceLoad(file.listFiles(), basePath, uriStart);
            } else {
                String binPath = file.toString().substring(basePath.length());
                String rFileName = uriStart + binPath.replace("\\", "/");
                try (InputStream inputStream = NativeImageUtils.class.getResourceAsStream(rFileName)) {
                    if (Objects.nonNull(inputStream)) {
                        LOGGER.info("Native image add filename " + rFileName);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void routerMethodInvoke(ApplicationContext applicationContext, RequestConfig requestConfig, ResponseConfig responseConfig) {
        applicationContext.getServerConfig().getRouter().getRouterMap().keySet().forEach((key) -> {
            try {
                HttpRequest httpRequest = HttpRequestBuilder.buildRequest(HttpMethod.GET, key, "127.0.0.1", "NativeImageAgent", requestConfig, applicationContext);
                new MethodInterceptor().doInterceptor(httpRequest, new SimpleHttpResponse(httpRequest, responseConfig));
                LOGGER.info("Native image agent call request " + key + " success");
            } catch (Exception e) {
                LOGGER.warning("Native image agent call request error -> " + LoggerUtil.recordStackTraceMsg(e));
            }
        });
        new LocalFileStaticResourceLoader(true, "/" + System.currentTimeMillis(), PathUtil.getRootPath()).getInputStream(PathUtil.getRootPath());

    }
}
