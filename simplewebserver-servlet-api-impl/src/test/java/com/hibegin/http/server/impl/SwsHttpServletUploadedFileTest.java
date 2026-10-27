package com.hibegin.http.server.impl;

import com.hibegin.common.util.IOUtil;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.UploadedFile;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class SwsHttpServletUploadedFileTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fileAndMetadataAccessShareOneLazyDecode() throws Exception {
        String oldTempPath = System.getProperty("sws.temp.path");
        System.setProperty("sws.temp.path", temporaryFolder.getRoot().getAbsolutePath());
        try {
            for (boolean metadataFirst : new boolean[]{true, false}) {
                SwsHttpServletRequestWrapper request = request();
                try {
                    File file = metadataFirst ? request.getUploadedFile("file").getFile() : request.getFile("file");
                    UploadedFile upload = request.getUploadedFile("file");
                    assertSame(file, upload.getFile());
                    assertSame(file, request.getFile("file"));
                    assertSame(upload, request.getUploadedFile("file"));
                    assertEquals("file", upload.getFieldName());
                    assertEquals("原始文件.txt", upload.getOriginalFilename());
                    assertEquals("text/plain", upload.getContentType());
                    assertEquals(7, upload.getSize());
                    assertFalse(upload.isEmpty());
                    try (InputStream first = upload.getInputStream(); InputStream second = upload.getInputStream()) {
                        assertEquals('c', first.read());
                        assertArrayEquals("content".getBytes(StandardCharsets.UTF_8), IOUtil.getByteByInputStream(second));
                        assertEquals('o', first.read());
                    }
                    assertNull(request.getUploadedFile("missing"));
                    request.deleteTempUploadFiles();
                    assertFalse(file.exists());
                    assertNull(request.getUploadedFile("file"));
                } finally {
                    request.deleteTempUploadFiles();
                }
            }
            assertEquals(0, temporaryFolder.getRoot().list().length);
        } finally {
            if (oldTempPath == null) System.clearProperty("sws.temp.path");
            else System.setProperty("sws.temp.path", oldTempPath);
        }
    }

    private SwsHttpServletRequestWrapper request() {
        byte[] body = ("--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"原始文件.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\ncontent\r\n--boundary--\r\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bytes = new ByteArrayInputStream(body);
        ServletInputStream input = new ServletInputStream() {
            @Override public int read() { return bytes.read(); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
        };
        ServletContext context = (ServletContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ServletContext.class}, (p, method, args) -> {
                    if ("getServerInfo".equals(method.getName())) return "ServletTest/1";
                    if ("getRealPath".equals(method.getName())) return temporaryFolder.getRoot().getAbsolutePath();
                    return null;
                });
        HttpServletRequest servlet = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "getServletContext": return context;
                        case "getLocalPort": return 18080;
                        case "getParameterMap": return new HashMap<String, String[]>();
                        case "getMethod": return "POST";
                        case "getContextPath": return "/blog";
                        case "getRequestURI": return "/blog/upload";
                        case "getScheme": return "http";
                        case "getHeaderNames": return Collections.enumeration(Collections.singleton("Content-Type"));
                        case "getHeader": return "multipart/form-data; boundary=boundary";
                        case "getInputStream": return input;
                        default: return null;
                    }
                });
        return new SwsHttpServletRequestWrapper(servlet, new RequestConfig(), new ApplicationContext(new ServerConfig()));
    }
}
