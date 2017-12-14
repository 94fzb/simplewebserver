package com.hibegin.http.server.web;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.util.MimeTypeUtil;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodInterceptor implements Interceptor {
    private static final Logger LOGGER = LoggerUtil.getLogger(MethodInterceptor.class);

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        boolean next = true;
        for (Map.Entry<String, String> entry : request.getServerConfig().getStaticResourceMapper().entrySet()) {
            if (request.getUri().startsWith(entry.getKey())) {
                String path = request.getUri().substring(entry.getKey().length());
                if (request.getUri().endsWith("/")) {
                    path += request.getServerConfig().getWelcomeFile();
                }
                InputStream inputStream = MethodInterceptor.class.getResourceAsStream(entry.getValue() + path);
                if (inputStream != null) {
                    if (path.contains(".")) {
                        response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(path.substring(path.lastIndexOf(".") + 1)));
                    }
                    response.write(inputStream);
                } else {
                    response.renderCode(404);
                }
                next = false;
                break;
            }
        }
        if (next) {
            Method method;
            Router router = request.getRequestConfig().getRouter();
            if (request.getUri().contains("-")) {
                method = router.getMethod(request.getUri().substring(0, request.getUri().indexOf("-")));
            } else {
                method = router.getMethod(request.getUri());
            }
            if (method == null) {
                File file = new File(request.getRealPath() + request.getUri());
                // 在请求路径中存在了. 认为其为文件
                if (file.exists() && !file.isDirectory() || request.getUri().contains(".")) {
                    response.writeFile(file);
                    return false;
                }
                if (request.getUri().endsWith("/")) {
                    response.renderHtml(request.getUri() + request.getServerConfig().getWelcomeFile());
                } else {
                    response.renderCode(404);
                }
                return false;
            }
            try {
                Controller controller = null;
                Constructor[] constructors = method.getDeclaringClass().getConstructors();
                boolean haveDefaultConstructor = false;
                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 2) {
                        if (constructor.getParameterTypes()[0].getName().equals(HttpRequest.class.getName()) &&
                                constructor.getParameterTypes()[1].getName().equals(HttpResponse.class.getName())) {
                            controller = (Controller) constructor.newInstance(request, response);
                        }
                    }
                    if (constructor.getParameterTypes().length == 0) {
                        haveDefaultConstructor = true;
                    }
                }
                if (controller == null) {
                    if (haveDefaultConstructor) {
                        controller = (Controller) method.getDeclaringClass().newInstance();
                        controller.request = request;
                        controller.response = response;
                    } else {
                        LOGGER.log(Level.WARNING, method.getDeclaringClass().getSimpleName() + " not find default constructor");
                        return false;
                    }
                }
                method.invoke(controller);
            } catch (Exception e) {
                response.renderCode(500);
                LOGGER.log(Level.SEVERE, "invoke error ", e);
                return false;
            }
        }
        return true;
    }
}
