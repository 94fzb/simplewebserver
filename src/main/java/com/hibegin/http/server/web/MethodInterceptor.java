package com.hibegin.http.server.web;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.StaticResourceLoader;
import com.hibegin.http.server.util.MimeTypeUtil;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerUtil.getLogger(MethodInterceptor.class);

    private void handleByStaticResource(HttpRequest request, HttpResponse response) {
        for (Map.Entry<String, Map.Entry<String, StaticResourceLoader>> entry :
                request.getServerConfig().getStaticResourceMapper().entrySet()) {
            if (request.getUri().startsWith(entry.getKey())) {
                String path = request.getUri().substring(entry.getKey().length());
                if (request.getUri().endsWith("/")) {
                    path += request.getServerConfig().getWelcomeFile();
                }
                InputStream inputStream = entry.getValue().getValue().getInputStream(entry.getValue().getKey() + path);
                if (inputStream != null) {
                    if (path.contains(".")) {
                        response.addHeader("Content-Type",
                                MimeTypeUtil.getMimeStrByExt(path.substring(path.lastIndexOf(".") + 1)));
                    }
                    response.write(inputStream);
                    return;
                }
            }
        }
        response.renderCode(404);
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        Router router = request.getRequestConfig().getRouter();
        Method method = router.getMethod(request.getUri());
        if (method == null) {
            handleByStaticResource(request, response);
            return true;
        }
        Controller controller = null;
        Constructor[] constructors = method.getDeclaringClass().getConstructors();
        boolean haveDefaultConstructor = false;
        for (Constructor constructor : constructors) {
            if (constructor.getParameterTypes().length == 2) {
                if (constructor.getParameterTypes()[0].getName().equals(HttpRequest.class.getName()) && constructor.getParameterTypes()[1].getName().equals(HttpResponse.class.getName())) {
                    controller = (Controller) constructor.newInstance(request, response);
                }
            }
            if (constructor.getParameterTypes().length == 0) {
                haveDefaultConstructor = true;
            }
        }
        if (controller == null) {
            if (haveDefaultConstructor) {
                controller = (Controller) method.getDeclaringClass().getDeclaredConstructor().newInstance();
                controller.request = request;
                controller.response = response;
            } else {
                LOGGER.log(Level.WARNING, method.getDeclaringClass().getSimpleName() + " not find default " +
                        "constructor");
                return false;
            }
        }
        method.invoke(controller);
        return true;
    }
}
