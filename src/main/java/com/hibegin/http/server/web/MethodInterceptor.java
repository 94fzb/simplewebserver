package com.hibegin.http.server.web;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.LocalFileStaticResourceLoader;
import com.hibegin.http.server.config.StaticResourceLoader;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.StatusCodeUtil;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class MethodInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerUtil.getLogger(MethodInterceptor.class);

    private boolean isMatch(String requestUri, String location) {
        if (location.endsWith("/")) {
            return requestUri.startsWith(location.substring(0, location.length() - 1));
        }
        return requestUri.startsWith(location);
    }

    private String extractPath(String requestUri, String location){
        if(requestUri.length() <= location.length()){
            return "/";
        }
        return requestUri.substring(location.length());
    }

    private void handleByStaticResource(HttpRequest request, HttpResponse response) {
        for (Map.Entry<String, Map.Entry<String, StaticResourceLoader>> entry : request.getServerConfig().getStaticResourceMapper().entrySet()) {
            if (isMatch(request.getUri(), entry.getKey())) {
                String path = extractPath(request.getUri(),entry.getKey());
                InputStream inputStream = null;
                if (entry.getValue().getValue() instanceof LocalFileStaticResourceLoader) {
                    inputStream = entry.getValue().getValue().getInputStream(entry.getValue().getKey() + path);
                }
                if (Objects.isNull(inputStream)) {
                    if (request.getUri().endsWith("/")) {
                        path += request.getServerConfig().getWelcomeFile();
                    }
                    inputStream = entry.getValue().getValue().getInputStream(entry.getValue().getKey() + path);
                }
                if (Objects.nonNull(inputStream)) {
                    if (path.contains(".")) {
                        String contentType = MimeTypeUtil.getMimeStrByExt(request.getUri().substring(request.getUri().lastIndexOf(".") + 1));
                        response.addHeader("Content-Type", contentType);
                    } else if (entry.getValue().getValue() instanceof LocalFileStaticResourceLoader) {
                        response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt("html"));
                    }
                    response.write(inputStream);
                    return;
                }
            }
        }
        File file = PathUtil.getStaticFile(request.getUri());
        if (file.exists() && file.isFile()) {
            response.writeFile(file);
            return;
        } else if (file.exists() && file.isDirectory()) {
            File welcomeFile = PathUtil.getStaticFile(request.getUri() + "/" + request.getServerConfig().getWelcomeFile());
            if (welcomeFile.exists()) {
                response.writeFile(welcomeFile);
                return;
            }
        }

        HttpErrorHandle errorHandle = request.getServerConfig().getErrorHandle(404);
        if (errorHandle == null) {
            response.renderCode(404);
        } else {
            errorHandle.doHandle(request, response, new NotFindResourceException(StatusCodeUtil.getStatusCodeDesc(404)));
        }
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        Router router = request.getRequestConfig().getRouter();
        Method method = router.getMethod(request.getUri());
        if (method == null) {
            handleByStaticResource(request, response);
            return false;
        }

        Object invoke = method.invoke(Controller.buildController(method, request, response));
        ResponseBody annotation = method.getAnnotation(ResponseBody.class);
        if (Objects.nonNull(annotation)) {
            response.renderJson(invoke);
        }
        return true;
    }
}
