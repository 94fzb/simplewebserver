package com.hibegin.http.server.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.config.StaticResourceCachePolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MethodInterceptorCacheTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void revalidatePolicyReturnsValidatorsAndHandlesIfNoneMatch() throws Exception {
        Files.write(temporaryFolder.newFile("main.js").toPath(), "main();".getBytes(StandardCharsets.UTF_8));
        ServerConfig serverConfig = config(StaticResourceCachePolicy.REVALIDATE);

        RecordingResponse initial = new RecordingResponse();
        new MethodInterceptor().doInterceptor(request(serverConfig, new LinkedHashMap<>()), initial.proxy());

        assertEquals(1, initial.writeCount);
        assertNull(initial.statusCode);
        assertEquals("no-cache", initial.headers.get("Cache-Control"));
        assertEquals("Accept-Encoding", initial.headers.get("Vary"));
        assertNotNull(initial.headers.get("Last-Modified"));
        String entityTag = initial.headers.get("ETag");
        assertNotNull(entityTag);

        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("If-None-Match", entityTag);
        RecordingResponse conditional = new RecordingResponse();
        new MethodInterceptor().doInterceptor(request(serverConfig, requestHeaders), conditional.proxy());

        assertEquals(Integer.valueOf(304), conditional.statusCode);
        assertEquals(0, conditional.writeCount);
        assertEquals(entityTag, conditional.headers.get("ETag"));
        assertEquals("no-cache", conditional.headers.get("Cache-Control"));
    }

    @Test
    public void cacheHeadersAreAbsentUnlessPolicyIsExplicitlyEnabled() throws Exception {
        Files.write(temporaryFolder.newFile("main.js").toPath(), "main();".getBytes(StandardCharsets.UTF_8));
        RecordingResponse response = new RecordingResponse();
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.addLocalFileStaticResourceMapper("/", temporaryFolder.getRoot().getAbsolutePath());

        new MethodInterceptor().doInterceptor(
                request(serverConfig, new LinkedHashMap<>()), response.proxy());

        assertEquals(1, response.writeCount);
        assertFalse(response.headers.containsKey("Cache-Control"));
        assertFalse(response.headers.containsKey("ETag"));
        assertFalse(response.headers.containsKey("Last-Modified"));
    }

    private ServerConfig config(StaticResourceCachePolicy cachePolicy) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.addLocalFileStaticResourceMapper(
                "/", temporaryFolder.getRoot().getAbsolutePath(), cachePolicy);
        return serverConfig;
    }

    private HttpRequest request(ServerConfig serverConfig, Map<String, String> headers) {
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(serverConfig.getRouter());
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContextPath":
                    return "";
                case "getUri":
                    return "/main.js";
                case "getMethod":
                    return HttpMethod.GET;
                case "getServerConfig":
                    return serverConfig;
                case "getRequestConfig":
                    return requestConfig;
                case "getHeader":
                    return headers.get(args[0]);
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(), new Class[]{HttpRequest.class}, handler);
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    private static class RecordingResponse implements InvocationHandler {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Integer statusCode;
        private int writeCount;

        private HttpResponse proxy() {
            return (HttpResponse) Proxy.newProxyInstance(
                    HttpResponse.class.getClassLoader(), new Class[]{HttpResponse.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "addHeader":
                    headers.put((String) args[0], (String) args[1]);
                    return null;
                case "getHeader":
                    return headers;
                case "renderCode":
                    statusCode = (Integer) args[0];
                    return null;
                case "write":
                    writeCount++;
                    if (args != null && args.length > 0 && args[0] instanceof InputStream) {
                        ((InputStream) args[0]).close();
                    }
                    return null;
                default:
                    return null;
            }
        }
    }
}
