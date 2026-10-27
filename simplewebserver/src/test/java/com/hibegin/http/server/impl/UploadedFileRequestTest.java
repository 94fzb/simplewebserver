package com.hibegin.http.server.impl;

import com.hibegin.common.util.IOUtil;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.UploadedFile;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class UploadedFileRequestTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final List<SimpleHttpRequest> requests = new ArrayList<>();
    private String oldTempPath;

    @Before
    public void setUp() {
        oldTempPath = System.getProperty("sws.temp.path");
        System.setProperty("sws.temp.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() {
        for (SimpleHttpRequest request : requests) request.deleteTempUploadFiles();
        if (oldTempPath == null) System.clearProperty("sws.temp.path");
        else System.setProperty("sws.temp.path", oldTempPath);
    }

    @Test
    public void metadataAndIndependentStreamsReferToTheExistingTemporaryFile() throws Exception {
        byte[] bytes = {0, 1, 2, (byte) 255, 13, 10, 42};
        byte[] body = body("name=\"file\"; filename=\"原始插件包.bin\"", "application/octet-stream", bytes);
        SimpleHttpRequest request = request(body);
        UploadedFile upload = request.getUploadedFile("file");
        assertEquals("file", upload.getFieldName());
        assertEquals("原始插件包.bin", upload.getOriginalFilename());
        assertEquals("application/octet-stream", upload.getContentType());
        assertEquals(bytes.length, upload.getSize());
        assertFalse(upload.isEmpty());
        assertSame(request.getFile("file"), upload.getFile());
        assertSame(upload, request.getUploadedFile("file"));
        assertNotEquals("原始插件包.bin", upload.getFile().getName());
        assertTrue(upload.getFile().getName().endsWith(".bin"));
        assertNull(request.getUploadedFile("missing"));
        try (InputStream first = upload.getInputStream(); InputStream second = upload.getInputStream()) {
            assertEquals(0, first.read());
            assertArrayEquals(bytes, IOUtil.getByteByInputStream(second));
            assertEquals(1, first.read());
        }
        assertArrayEquals(body, request.getRequestBodyByteBuffer().array());
        request.deleteTempUploadFiles();
        assertFalse(upload.getFile().exists());
        assertNull(request.getUploadedFile("file"));
        assertEquals(bytes.length, upload.getSize());
        assertThrows(IOException.class, upload::getInputStream);
        assertEquals(0, temporaryFolder.getRoot().list().length);
    }

    @Test
    public void quotedFilenameKeepsSemicolonsEqualsAndClientPathsAsMetadata() throws Exception {
        String original = "C:\\fakepath\\报告;v=1\"final.txt";
        String quoted = original.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] bytes = "contents".getBytes(StandardCharsets.UTF_8);
        SimpleHttpRequest request = request(body("filename=\"" + quoted + "\"; name=\"attachment\"",
                "text/plain; charset=UTF-8", bytes));
        UploadedFile upload = request.getUploadedFile("attachment");
        assertEquals(original, upload.getOriginalFilename());
        assertEquals("text/plain; charset=UTF-8", upload.getContentType());
        assertEquals(temporaryFolder.getRoot(), upload.getFile().getParentFile());
        assertArrayEquals(bytes, Files.readAllBytes(upload.getFile().toPath()));
    }

    @Test
    public void emptyFileAndAbsentMetadataRemainDistinct() throws Exception {
        for (String filename : new String[]{"empty.txt", ""}) {
            UploadedFile upload = request(body("name=\"file\"; filename=\"" + filename + "\"", null,
                    new byte[0])).getUploadedFile("file");
            assertEquals(filename, upload.getOriginalFilename());
            assertNull(upload.getContentType());
            assertEquals(0, upload.getSize());
            assertTrue(upload.isEmpty());
            try (InputStream input = upload.getInputStream()) { assertEquals(-1, input.read()); }
        }
        SimpleHttpRequest legacy = new SimpleHttpRequest(null, new ApplicationContext(serverConfig()), new RequestConfig());
        legacy.files = new HashMap<>();
        File file = temporaryFolder.newFile("generated.txt");
        Files.write(file.toPath(), new byte[]{1});
        legacy.files.put("file", file);
        requests.add(legacy);
        UploadedFile upload = legacy.getUploadedFile("file");
        assertSame(file, upload.getFile());
        assertNull(upload.getOriginalFilename());
        assertNull(upload.getContentType());
        assertEquals(1, upload.getSize());
    }

    @Test
    public void legacyDecoderStillReturnsTheFileContents() throws Exception {
        byte[] bytes = {9, 8, 7};
        Map<String, File> files = HttpRequestDecoderImpl.getFiles(serverConfig(),
                body("name=\"file\"; filename=\"legacy.dat\"", null, bytes));
        try {
            assertArrayEquals(bytes, Files.readAllBytes(files.get("file").toPath()));
        } finally {
            for (File file : files.values()) Files.delete(file.toPath());
        }
    }

    private SimpleHttpRequest request(byte[] body) throws Exception {
        byte[] headers = ("POST /upload HTTP/1.1\r\nHost: example.com\r\n"
                + "Content-Type: multipart/form-data; boundary=boundary\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        RequestConfig config = new RequestConfig();
        config.setMaxRequestBodySize(1024 * 1024);
        HttpRequestDecoderImpl decoder = new HttpRequestDecoderImpl(config, new ApplicationContext(serverConfig()), null);
        assertTrue(decoder.doDecode(ByteBuffer.allocate(headers.length + body.length).put(headers).put(body)).getKey());
        SimpleHttpRequest request = (SimpleHttpRequest) decoder.getRequest();
        requests.add(request);
        return request;
    }

    private byte[] body(String disposition, String contentType, byte[] bytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--boundary\r\ncontent-disposition: form-data; " + disposition + "\r\n"
                + (contentType == null ? "" : "content-type:" + contentType + "\r\n")
                + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n--boundary--\r\n".getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private ServerConfig serverConfig() {
        ServerConfig config = new ServerConfig();
        config.setPort(18080);
        return config;
    }
}
