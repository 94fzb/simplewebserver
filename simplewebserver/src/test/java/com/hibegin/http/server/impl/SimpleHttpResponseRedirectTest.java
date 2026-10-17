package com.hibegin.http.server.impl;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SimpleHttpResponseRedirectTest {

    @Test
    public void redirectPreservesCallerEncodedPathAndQueryInTheWireResponse() throws Exception {
        String message = "请求失败 &next=/example?value=1#section + 50% 😀\r\nLocation: /else";
        String query = "message=" + URLEncoder.encode(message, "UTF-8");
        String path = "/admin/%E9%A1%B5%E9%9D%A2";
        for (String contextPath : new String[]{null, "", "/", "/blog"}) {
            CapturingResponse response = response(contextPath);

            response.redirect(path + "?" + query);

            String prefix = contextPath == null || "/".equals(contextPath) ? "" : contextPath;
            String location = response.getHeader().get("Location");
            assertEquals(prefix + path + "?" + query, location);
            assertEquals(message, URLDecoder.decode(URI.create(location).getRawQuery().substring("message=".length()), "UTF-8"));
            assertWireRedirect(response, location);
        }
    }

    @Test
    public void redirectPreservesAbsoluteAndRelativeUrlsWithoutAddingContextPath() {
        for (String url : new String[]{
                "https://example.com/a%20b?token=x%2By%3D&next=%2Fpage#part%201",
                "next%20page?value=100%25#part%201",
                "?return=%2Fadmin%3Fpage%3D2",
                "#part%201"}) {
            CapturingResponse response = response("/blog");

            response.redirect(url);

            assertEquals(url, response.getHeader().get("Location"));
            assertWireRedirect(response, url);
        }
    }

    @Test
    public void forwardPreservesCallerEncodedUri() {
        CapturingResponse response = response("/blog");
        String uri = "/next%20page?message=%E4%B8%AD%E6%96%87&value=100%25";

        response.forward(uri);

        String expected = "https://example.com/blog" + uri;
        assertEquals(expected, response.getHeader().get("Location"));
        assertWireRedirect(response, expected);
    }

    @Test
    public void redirectRejectsRawHeaderLineBreaksBeforeWritingAResponse() {
        for (String url : new String[]{"/next\rLocation: /injected", "https://example.com/\nInjected: yes", "/next\r\nInjected: yes"}) {
            CapturingResponse response = response("/blog");
            try {
                response.redirect(url);
                fail("Expected CR/LF rejection");
            } catch (IllegalArgumentException expected) {
                assertFalse(response.getHeader().containsKey("Location"));
                assertEquals("", response.wire());
            }
        }
    }

    private void assertWireRedirect(CapturingResponse response, String location) {
        String wire = response.wire();
        assertTrue(wire.startsWith("HTTP/1.1 302 "));
        assertTrue(wire.contains("\r\nLocation: " + location + "\r\n"));
        assertTrue(wire.contains("\r\nContent-Length: 0\r\n"));
        assertTrue(wire.endsWith("\r\n\r\n"));
    }

    private CapturingResponse response(String contextPath) {
        ServerConfig config = new ServerConfig();
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContextPath": return contextPath;
                        case "getServerConfig": return config;
                        case "getHttpVersion": return HttpVersion.HTTP_1_1;
                        case "getMethod": return HttpMethod.GET;
                        case "getScheme": return "https";
                        case "getHeader": return "Host".equals(args[0]) ? "example.com" : null;
                        default: return null;
                    }
                });
        return new CapturingResponse(request);
    }

    private static class CapturingResponse extends SimpleHttpResponse {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private CapturingResponse(HttpRequest request) {
            super(request, new ResponseConfig());
        }

        @Override
        protected void send(byte[] bytes, boolean body, boolean close) {
            output.write(bytes, 0, bytes.length);
        }

        private String wire() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
