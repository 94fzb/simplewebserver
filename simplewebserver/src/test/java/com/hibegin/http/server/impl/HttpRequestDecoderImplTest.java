package com.hibegin.http.server.impl;

import com.hibegin.common.util.IOUtil;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HttpRequestDecoderImplTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String oldTempPath;

    @Before
    public void setUp() {
        oldTempPath = System.getProperty("sws.temp.path");
        System.setProperty("sws.temp.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() {
        if (oldTempPath == null) {
            System.clearProperty("sws.temp.path");
        } else {
            System.setProperty("sws.temp.path", oldTempPath);
        }
    }

    @Test
    public void connectBodyRemainsStreamableWithoutFormParsing() throws Exception {
        HttpRequestDecoderImpl decoder = decoder();
        decoder.doDecode(ByteBuffer.wrap((
                "CONNECT example.com:443 HTTP/1.1\r\n"
                        + "Host: example.com:443\r\n"
                        + "Content-Type: application/x-www-form-urlencoded\r\n"
                        + "\r\n").getBytes(StandardCharsets.UTF_8)));

        byte[] tunnelData = "secret=value".getBytes(StandardCharsets.UTF_8);
        decoder.doDecode(ByteBuffer.wrap(tunnelData));

        assertFalse(decoder.getRequest().getParamMap().containsKey("secret"));
        try (InputStream inputStream = decoder.getRequest().getInputStream()) {
            assertArrayEquals(tunnelData, IOUtil.getByteByInputStream(inputStream));
        }
    }

    @Test
    public void urlEncodedBodyStillPopulatesParameters() throws Exception {
        HttpRequestDecoderImpl decoder = decoder();
        byte[] body = "name=value".getBytes(StandardCharsets.UTF_8);

        decoder.doDecode(ByteBuffer.wrap((
                "POST /submit HTTP/1.1\r\n"
                        + "Host: example.com\r\n"
                        + "Content-Type: application/x-www-form-urlencoded\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "\r\n"
                        + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));

        assertEquals("value", decoder.getRequest().getParamMap().get("name")[0]);
    }

    @Test
    public void unsupportedContentTypeDoesNotReadTempFile() throws Exception {
        ServerConfig serverConfig = serverConfig();
        serverConfig.setHttpRequestDecodeListener((request, bytes) ->
                ((SimpleHttpRequest) request).tmpRequestBodyFile = temporaryFolder.getRoot());
        HttpRequestDecoderImpl decoder = decoder(serverConfig);
        byte[] body = "{\"name\":\"value\"}".getBytes(StandardCharsets.UTF_8);

        decoder.doDecode(ByteBuffer.wrap((
                "POST /submit HTTP/1.1\r\n"
                        + "Host: example.com\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "\r\n"
                        + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));

        assertFalse(decoder.getRequest().getParamMap().containsKey("name"));
    }

    private HttpRequestDecoderImpl decoder() {
        return decoder(serverConfig());
    }

    private HttpRequestDecoderImpl decoder(ServerConfig serverConfig) {
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setMaxRequestBodySize(1024 * 1024);
        return new HttpRequestDecoderImpl(
                requestConfig, new ApplicationContext(serverConfig), null);
    }

    private ServerConfig serverConfig() {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setPort(18080);
        return serverConfig;
    }
}
