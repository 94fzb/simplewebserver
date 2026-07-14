package com.hibegin.http.server.impl;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimpleHttpResponseStatusTest {

    @Test
    public void notModifiedHasNoRedirectHeadersOrBody() {
        CapturingHandler handler = new CapturingHandler();
        ServerConfig serverConfig = serverConfig();
        serverConfig.setWelcomeFile("");

        new SimpleHttpResponse(request(serverConfig, handler), new ResponseConfig()).renderCode(304);

        String response = handler.response();
        assertTrue(response.startsWith("HTTP/1.1 304 "));
        assertFalse(response.contains("Location:"));
        assertFalse(response.toLowerCase().contains("content-length:"));
        assertFalse(response.toLowerCase().contains("transfer-encoding:"));
        assertTrue(response.endsWith("\r\n\r\n"));
    }

    @Test
    public void noContentRemovesBodyFramingHeaders() {
        CapturingHandler handler = new CapturingHandler();
        SimpleHttpResponse response = new SimpleHttpResponse(
                request(serverConfig(), handler), new ResponseConfig());
        response.addHeader("content-length", "9");
        response.addHeader("transfer-encoding", "chunked");

        response.renderCode(204);

        String rawResponse = handler.response();
        assertTrue(rawResponse.startsWith("HTTP/1.1 204 "));
        assertFalse(rawResponse.toLowerCase().contains("content-length:"));
        assertFalse(rawResponse.toLowerCase().contains("transfer-encoding:"));
        assertTrue(rawResponse.endsWith("\r\n\r\n"));
    }

    @Test
    public void redirectRetainsLocationAndExplicitEmptyLength() {
        CapturingHandler handler = new CapturingHandler();
        SimpleHttpResponse response = new SimpleHttpResponse(
                request(serverConfig(), handler), new ResponseConfig());
        response.addHeader("Location", "/target");

        response.renderCode(302);

        String rawResponse = handler.response();
        assertTrue(rawResponse.startsWith("HTTP/1.1 302 "));
        assertTrue(rawResponse.contains("Location: /target\r\n"));
        assertTrue(rawResponse.contains("Content-Length: 0\r\n"));
        assertTrue(rawResponse.endsWith("\r\n\r\n"));
    }

    @Test
    public void headResponseKeepsRepresentationLengthButSendsNoBody() {
        CapturingHandler handler = new CapturingHandler();
        SimpleHttpResponse response = new SimpleHttpResponse(
                request(serverConfig(), handler, HttpMethod.HEAD), new ResponseConfig());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write('o');
        body.write('k');

        response.write(body, 200);

        String rawResponse = handler.response();
        assertTrue(rawResponse.startsWith("HTTP/1.1 200 "));
        assertTrue(rawResponse.contains("Content-Length: 2\r\n"));
        assertTrue(rawResponse.endsWith("\r\n\r\n"));
    }

    private ServerConfig serverConfig() {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setServerInfo("SimpleWebServer-Test");
        return serverConfig;
    }

    private HttpRequest request(ServerConfig serverConfig, ReadWriteSelectorHandler handler) {
        return request(serverConfig, handler, HttpMethod.GET);
    }

    private HttpRequest request(ServerConfig serverConfig, ReadWriteSelectorHandler handler, HttpMethod httpMethod) {
        InvocationHandler requestHandler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getHandler":
                    return handler;
                case "getServerConfig":
                    return serverConfig;
                case "getHttpVersion":
                    return HttpVersion.HTTP_1_1;
                case "getMethod":
                    return httpMethod;
                case "getHeader":
                    return null;
                case "getScheme":
                    return "http";
                case "getUri":
                    return "/resource";
                default:
                    return null;
            }
        };
        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(), new Class[]{HttpRequest.class}, requestHandler);
    }

    private static class CapturingHandler implements ReadWriteSelectorHandler {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        @Override
        public void handleWrite(ByteBuffer byteBuffer) {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            outputStream.write(bytes, 0, bytes.length);
        }

        @Override
        public ByteBuffer handleRead() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public SocketChannel getChannel() {
            return null;
        }

        @Override
        public boolean isPlain() {
            return true;
        }

        private String response() {
            return new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
