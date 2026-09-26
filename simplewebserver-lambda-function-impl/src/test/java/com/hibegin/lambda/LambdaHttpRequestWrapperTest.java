package com.hibegin.lambda;

import com.google.gson.JsonParser;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.SimpleHttpRequest;
import com.hibegin.lambda.rest.ApiGatewayHttp;
import com.hibegin.lambda.rest.ApiGatewayRequestContext;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class LambdaHttpRequestWrapperTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<SimpleHttpRequest> requests = new ArrayList<>();
    private String oldTempPath;

    @Before
    public void setUp() {
        oldTempPath = System.getProperty("sws.temp.path");
        System.setProperty("sws.temp.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        try {
            for (SimpleHttpRequest request : requests) {
                request.getInputStream().close();
                request.deleteTempUploadFiles();
            }
        } finally {
            if (oldTempPath == null) {
                System.clearProperty("sws.temp.path");
            } else {
                System.setProperty("sws.temp.path", oldTempPath);
            }
        }
    }

    @Test
    public void jsonRpcBodyMatchesWebAndRemainsParseable() throws Exception {
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}")
                .getBytes(StandardCharsets.UTF_8);
        SimpleHttpRequest web = webRequest(body, "application/json");
        SimpleHttpRequest lambda = lambdaRequest(body, false, "application/json");

        assertBodyEquals(body, web);
        assertBodyEquals(body, lambda);
        assertEquals("initialize", JsonParser.parseString(StandardCharsets.UTF_8
                .decode(lambda.getRequestBodyByteBuffer()).toString())
                .getAsJsonObject().get("method").getAsString());
    }

    @Test
    public void unicodeTextAndBase64BodiesMatchWeb() throws Exception {
        byte[] body = "{\"query\":\"Ubuntu 中文博客 \uD83D\uDE80\"}".getBytes(StandardCharsets.UTF_8);
        assertBodyEquals(body, webRequest(body, "application/json; charset=UTF-8"));
        for (boolean base64 : new boolean[]{false, true}) {
            SimpleHttpRequest lambda = lambdaRequest(body, base64, "application/json; charset=UTF-8");
            assertBodyEquals(body, lambda);
            assertArrayEquals(body, lambda.getInputStream().readAllBytes());
        }
    }

    @Test
    public void binaryBase64BodyMatchesWeb() throws Exception {
        byte[] body = {0, 1, 13, 10, 127, (byte) 128, (byte) 255};
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, "application/octet-stream"),
                lambdaRequest(body, true, "application/octet-stream"))) {
            assertBodyEquals(body, request);
            assertArrayEquals(body, request.getInputStream().readAllBytes());
        }
    }

    @Test
    public void bufferPositionAndMutationDoNotAffectLaterReads() throws Exception {
        byte[] body = "abcdef".getBytes(StandardCharsets.UTF_8);
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, "text/plain"),
                lambdaRequest(body, false, "text/plain"))) {
            ByteBuffer first = request.getRequestBodyByteBuffer();
            assertFalse(first.isReadOnly());
            assertTrue(first.hasArray());
            first.put(0, (byte) 'X');
            first.array()[1] = 'Y';
            first.position(first.limit());
            assertBodyEquals(body, request);
            assertArrayEquals(body, request.getInputStream().readAllBytes());
        }
    }

    @Test
    public void streamAndBufferReadsAreIndependent() throws Exception {
        byte[] body = "abcdef".getBytes(StandardCharsets.UTF_8);
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, "text/plain"),
                lambdaRequest(body, false, "text/plain"))) {
            InputStream stream = request.getInputStream();
            assertEquals('a', stream.read());
            assertBodyEquals(body, request);
            assertSame(stream, request.getInputStream());
            assertArrayEquals(Arrays.copyOfRange(body, 1, body.length), stream.readAllBytes());
            assertBodyEquals(body, request);
            assertEquals(-1, stream.read());
        }
    }

    @Test
    public void offsetReadsMatchWeb() throws Exception {
        byte[] body = "abcdef".getBytes(StandardCharsets.UTF_8);
        SimpleHttpRequest web = webRequest(body, "text/plain");
        SimpleHttpRequest lambda = lambdaRequest(body, false, "text/plain");
        for (int offset : new int[]{-1, 0, 1, 3, body.length, body.length + 1, Integer.MAX_VALUE}) {
            ByteBuffer expected = web.getRequestBodyByteBuffer(offset);
            ByteBuffer actual = lambda.getRequestBodyByteBuffer(offset);
            assertEquals("offset " + offset, expected, actual);
            assertEquals(0, actual.position());
            assertEquals(expected.capacity(), actual.capacity());
            assertEquals(expected.limit(), actual.limit());
            assertArrayEquals(expected.array(), actual.array());
        }
        lambda.getRequestBodyByteBuffer(1).put(0, (byte) 'X');
        assertBodyEquals(body, lambda);
    }

    @Test
    public void emptyAndAbsentBodiesMatchWeb() throws Exception {
        byte[] empty = new byte[0];
        assertBodyEquals(empty, webRequest(empty, null));
        for (byte[] body : new byte[][]{null, empty}) {
            for (boolean base64 : new boolean[]{false, true}) {
                SimpleHttpRequest lambda = lambdaRequest(body, base64, null);
                assertBodyEquals(empty, lambda);
                assertEquals(-1, lambda.getInputStream().read());
                assertEquals(0, lambda.getRequestBodyByteBuffer(1).remaining());
            }
        }
    }

    @Test
    public void bodyWithoutContentTypeMatchesWeb() throws Exception {
        byte[] body = "raw body".getBytes(StandardCharsets.UTF_8);
        assertBodyEquals(body, webRequest(body, null));
        assertBodyEquals(body, lambdaRequest(body, false, null));
    }

    @Test
    public void formParametersAndRawBodyMatchWeb() throws Exception {
        byte[] body = "name=Ubuntu&tag=linux&tag=blog".getBytes(StandardCharsets.UTF_8);
        for (String contentType : new String[]{"application/x-www-form-urlencoded",
                "application/x-www-form-urlencoded; charset=UTF-8"}) {
            SimpleHttpRequest web = webRequest(body, contentType);
            SimpleHttpRequest lambda = lambdaRequest(body, false, contentType);
            assertEquals(web.getParamMap().keySet(), lambda.getParamMap().keySet());
            for (String key : web.getParamMap().keySet()) {
                assertArrayEquals(web.getParamMap().get(key), lambda.getParamMap().get(key));
            }
            assertBodyEquals(body, lambda);
            assertArrayEquals(body, lambda.getInputStream().readAllBytes());
        }
    }

    @Test
    public void bodyLargerThan16KiBMatchesWeb() throws Exception {
        byte[] body = ("{\"text\":\"" + "x".repeat(32 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8);
        assertBodyEquals(body, webRequest(body, "application/json"));
        assertBodyEquals(body, lambdaRequest(body, false, "application/json"));
        assertBodyEquals(body, lambdaRequest(body, true, "application/json"));
    }

    @Test
    public void multipartFileAndRawBodyReadsAreIndependent() throws Exception {
        byte[] payload = "uploaded data".getBytes(StandardCharsets.UTF_8);
        byte[] body = multipartBody(payload);
        String contentType = "multipart/form-data; boundary=boundary";
        for (boolean streamFirst : new boolean[]{false, true}) {
            for (SimpleHttpRequest request : Arrays.asList(webRequest(body, contentType),
                    lambdaRequest(body, true, contentType))) {
                if (streamFirst) {
                    assertArrayEquals(body, request.getInputStream().readAllBytes());
                }
                assertNotNull(request.getFile("file"));
                assertArrayEquals(payload, Files.readAllBytes(request.getFile("file").toPath()));
                assertBodyEquals(body, request);
                if (!streamFirst) {
                    assertArrayEquals(body, request.getInputStream().readAllBytes());
                }
            }
        }
    }

    @Test
    public void cleanupDiscardsBodyLikeWeb() throws Exception {
        byte[] body = "body to clean up".getBytes(StandardCharsets.UTF_8);
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, "text/plain"),
                lambdaRequest(body, false, "text/plain"))) {
            request.deleteTempUploadFiles();
            assertBodyEquals(new byte[0], request);
            assertEquals(-1, request.getInputStream().read());
            request.deleteTempUploadFiles();
        }
        assertEquals(0, temporaryFolder.getRoot().list().length);
    }

    @Test
    public void cleanupClosesBodyStreamAndDeletesUpload() throws Exception {
        byte[] body = multipartBody("uploaded data".getBytes(StandardCharsets.UTF_8));
        String contentType = "multipart/form-data; boundary=boundary";
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, contentType),
                lambdaRequest(body, true, contentType))) {
            File upload = request.getFile("file");
            assertTrue(upload.exists());
            InputStream stream = request.getInputStream();
            assertEquals('-', stream.read());
            request.deleteTempUploadFiles();
            assertFalse(upload.exists());
            assertThrows(IOException.class, () -> stream.read());
            assertEquals(-1, request.getInputStream().read());
        }
        assertEquals(0, temporaryFolder.getRoot().list().length);
    }

    @Test
    public void nonMultipartContentTypeDoesNotExposeAnUpload() throws Exception {
        byte[] body = multipartBody("plain text".getBytes(StandardCharsets.UTF_8));
        for (SimpleHttpRequest request : Arrays.asList(webRequest(body, "text/plain"),
                lambdaRequest(body, false, "text/plain"))) {
            assertNull(request.getFile("file"));
            assertBodyEquals(body, request);
        }
    }

    @Test
    public void failedBodyInitializationDoesNotLeaveTemporaryFiles() {
        byte[] body = ("--boundary\r\nContent-Disposition: form-data\r\n\r\n"
                + "data\r\n--boundary--\r\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class,
                () -> lambdaRequest(body, false, "multipart/form-data; boundary=boundary"));
        assertEquals(0, temporaryFolder.getRoot().list().length);
    }

    private byte[] multipartBody(byte[] payload) {
        return ("--boundary\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + new String(payload, StandardCharsets.UTF_8) + "\r\n--boundary--\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private void assertBodyEquals(byte[] expected, SimpleHttpRequest request) {
        ByteBuffer body = request.getRequestBodyByteBuffer();
        assertEquals(0, body.position());
        assertEquals(expected.length, body.remaining());
        assertArrayEquals(expected, body.array());
    }

    private SimpleHttpRequest webRequest(byte[] body, String contentType) throws Exception {
        String headers = "POST /mcp?source=query HTTP/1.1\r\nHost: example.com\r\n"
                + (contentType == null ? "" : "Content-Type: " + contentType + "\r\n")
                + "Content-Length: " + body.length + "\r\n\r\n";
        byte[] headerBytes = headers.getBytes(StandardCharsets.UTF_8);
        ByteBuffer wire = ByteBuffer.allocate(headerBytes.length + body.length);
        wire.put(headerBytes).put(body).flip();
        HttpRequestDecoderImpl decoder = new HttpRequestDecoderImpl(
                requestConfig(), new ApplicationContext(serverConfig()), null);
        decoder.doDecode(wire);
        SimpleHttpRequest request = (SimpleHttpRequest) decoder.getRequest();
        requests.add(request);
        return request;
    }

    private SimpleHttpRequest lambdaRequest(byte[] body, boolean base64, String contentType) {
        LambdaApiGatewayRequest event = new LambdaApiGatewayRequest();
        event.setRawPath("/mcp");
        event.setRawQueryString("source=query");
        event.setHeaders(new HashMap<>());
        if (contentType != null) {
            event.getHeaders().put("content-type", contentType);
        }
        event.setBody(body == null ? null : base64 ? Base64.getEncoder().encodeToString(body)
                : new String(body, StandardCharsets.UTF_8));
        event.setBase64Encoded(base64);
        ApiGatewayHttp http = new ApiGatewayHttp();
        http.setMethod("POST");
        ApiGatewayRequestContext context = new ApiGatewayRequestContext();
        context.setHttp(http);
        context.setDomainName("example.com");
        event.setRequestContext(context);
        SimpleHttpRequest request = new LambdaHttpRequestWrapper(
                new ApplicationContext(serverConfig()), requestConfig(), event);
        requests.add(request);
        return request;
    }

    private RequestConfig requestConfig() {
        RequestConfig config = new RequestConfig();
        config.setMaxRequestBodySize(1024 * 1024);
        return config;
    }

    private ServerConfig serverConfig() {
        ServerConfig config = new ServerConfig();
        config.setPort(18080);
        return config;
    }
}
