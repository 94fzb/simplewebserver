package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.GsonHttpJsonMessageConverter;
import com.hibegin.http.server.config.LocalFileStaticResourceLoader;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.web.MethodInterceptor;
import com.hibegin.http.server.web.Router;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
                doLoadResource(rFileName);
            }
        }
    }

    private static void doLoadResource(String resourceName) {
        try (InputStream inputStream = NativeImageUtils.class.getResourceAsStream(resourceName)) {
            if (Objects.nonNull(inputStream)) {
                LOGGER.info("Native image add resource " + resourceName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void doResourceLoadByResourceNames(List<String> resourceNames) {
        for (final String resourceName : resourceNames) {
            doLoadResource(resourceName);
        }
    }

    /**
     * 快捷注册一个类的所有公开 get 方法，
     * 比如 freemarker 这类工具，在进行模板渲染的时候使用的是 get 方法。
     *
     * @param clazz 待注册的类名
     */
    public static void regGetMethodByClassName(Class<?> clazz) {
        Method[] declaredMethods = clazz.getMethods();
        for (Method method : declaredMethods) {
            method.setAccessible(true);
            if (method.getName().startsWith("get") && method.getParameterTypes().length == 0) {
                try {
                    method.invoke(clazz.newInstance());
                } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public static void routerMethodInvoke(ApplicationContext applicationContext, RequestConfig requestConfig, ResponseConfig responseConfig) {
        List<CompletableFuture<Void>> voidCompletableFutures = new ArrayList<>();
        for (Map.Entry<String, Method> methodInfo : applicationContext.getServerConfig().getRouter().getRouterMap().entrySet()) {
            HttpMethod httpMethod = ObjectUtil.requireNonNullElse(Router.getHttpMethod(Objects.requireNonNull(methodInfo.getValue())), HttpMethod.GET);
            String key = "[" + httpMethod + "][" + methodInfo.getKey() + "]";
            voidCompletableFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    HttpRequest httpRequest = HttpRequestBuilder.buildRequest(httpMethod, methodInfo.getKey(), "127.0.0.1", "NativeImageAgent", requestConfig, applicationContext);
                    HttpResponse httpResponse = new SimpleHttpResponse(httpRequest, responseConfig);
                    try {
                        new MethodInterceptor().doInterceptor(httpRequest, httpResponse);
                    } catch (InvocationTargetException e) {
                        HttpErrorHandle errorHandle = applicationContext.getServerConfig().getErrorHandle(500);
                        if (Objects.nonNull(errorHandle)) {
                            errorHandle.doHandle(httpRequest, httpResponse, e.getTargetException());
                        } else {
                            throw e;
                        }
                    } catch (Throwable e) {
                        HttpErrorHandle errorHandle = applicationContext.getServerConfig().getErrorHandle(500);
                        if (Objects.nonNull(errorHandle)) {
                            errorHandle.doHandle(httpRequest, httpResponse, e);
                        } else {
                            throw e;
                        }
                    }
                    LOGGER.info("Native image agent call request " + key + " success");
                } catch (Throwable e) {
                    LOGGER.warning("Native image agent call request " + key + " error -> " + LoggerUtil.recordStackTraceMsg(e));
                }
            }, applicationContext.getServerConfig().getRequestExecutor()));
        }
        CompletableFuture.allOf(voidCompletableFutures.toArray(new CompletableFuture[0])).join();
        new LocalFileStaticResourceLoader(true, "/" + System.currentTimeMillis(), PathUtil.getRootPath(), applicationContext.getServerConfig().getContextPath()).getInputStream(PathUtil.getRootPath());

    }

    public static void gsonNativeAgent(List<String> cls) {
        List<Class<?>> clzs = new ArrayList<>();
        for (String cl : cls) {
            try {
                clzs.add(Class.forName(cl));
            } catch (ClassNotFoundException e) {
                LOGGER.warning("Agent error " + cl + " - > " + LoggerUtil.recordStackTraceMsg(e));
            }
        }
        gsonNativeAgentByClazz(clzs);
    }

    public static void gsonNativeAgentByClazz(List<Class<?>> cls) {
        GsonHttpJsonMessageConverter gsonHttpJsonMessageConverter = new GsonHttpJsonMessageConverter();
        for (Class<?> cl : cls) {
            try {
                Class<?> clazz = Class.forName(cl.getName());
                Object o = gsonHttpJsonMessageConverter.fromJson("{}", clazz);
                gsonHttpJsonMessageConverter.toJson(o);
            } catch (Exception e) {
                LOGGER.warning("Agent error " + cl + " - > " + LoggerUtil.recordStackTraceMsg(e));
            }
        }
    }
}
