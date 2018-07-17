package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.io.ChunkedOutputStream;
import com.hibegin.http.io.GzipCompressingInputStream;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.execption.InternalException;
import com.hibegin.http.server.util.*;
import com.hibegin.http.server.web.cookie.Cookie;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class SimpleHttpResponse implements HttpResponse {

    private static final String CRLF = "\r\n";
    private static final int RESPONSE_BYTES_BLANK_SIZE = 4096;
    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpResponse.class);
    private Map<String, String> header = new HashMap<>();
    private HttpRequest request;
    private List<Cookie> cookieList = new ArrayList<>();
    private ResponseConfig responseConfig;
    private static final int SEND_FILE_BLANK_LENGTH = 1024 * 1024;
    private static final String SERVER_INFO = ServerInfo.getName() + "/" + ServerInfo.getVersion();

    public SimpleHttpResponse(HttpRequest request, ResponseConfig responseConfig) {
        this.request = request;
        this.responseConfig = responseConfig;
    }

    @Override
    public void writeFile(File file) {
        if (file.exists()) {
            FileInputStream fileInputStream = null;
            try {
                if (file.isDirectory()) {
                    renderByStatusCode(302);
                    return;
                }
                fileInputStream = new FileInputStream(file);
                String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                // getMimeType
                if (header.get("Content-Type") == null) {
                    header.put("Content-Type", MimeTypeUtil.getMimeStrByExt(ext));
                }
                if (file.length() < SEND_FILE_BLANK_LENGTH) {
                    send(buildResponseData(200, IOUtil.getByteByInputStream(fileInputStream)));
                } else {
                    send(wrapperResponseHeaderWithContentLength(200, file.length()), false);
                    //处理大文件
                    int length = SEND_FILE_BLANK_LENGTH;
                    byte tempByte[] = new byte[length];
                    while ((length = fileInputStream.read(tempByte)) != -1) {
                        send(BytesUtil.subBytes(tempByte, 0, length), false);
                    }
                    //是否关闭流
                    send(new byte[]{});
                }

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            } finally {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        //ignore
                    }
                }
            }
        } else {
            renderByStatusCode(404);
        }
    }

    private void send(byte[] bytes, boolean close) {
        try {
            if (bytes.length > 0) {
                request.getHandler().handleWrite(ByteBuffer.wrap(bytes));
            }
            if (close) {
                request.getHandler().close();
            }
        } catch (IOException e) {
            //LOGGER.log(Level.WARNING, "send error " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "send error", e);
            throw new InternalException("send error", e);
        }
    }

    public void send(ByteArrayOutputStream outputStream, boolean close) {
        send(outputStream.toByteArray(), close);
    }


    private void send(byte[] bytes) {
        send(bytes, "close".equalsIgnoreCase(getHeader().get("Connection")));
    }

    @Override
    public void renderJson(Object obj) {
        try {
            Object gson = Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance();
            renderByMimeType("json", ((String) Class.forName("com.google.gson.Gson").getMethod("toJson", Object.class).invoke(gson, obj)).getBytes());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            throw new InternalException(e);
        }
    }

    /**
     * @return
     */
    private byte[] buildResponseData(Integer statusCode, byte[] data) {
        byte[] headerBytes = wrapperResponseHeaderWithContentLength(statusCode, data.length);
        if (data.length == 0) {
            return headerBytes;
        } else {
            return BytesUtil.mergeBytes(headerBytes, tryConvertGzipBytes(data));
        }
    }

    private byte[] wrapperResponseHeaderWithContentLength(Integer statusCode, long length) {
        header.put("Content-Length", length + "");
        return wrapperBaseResponseHeader(statusCode);
    }

    private byte[] wrapperBaseResponseHeader(int statusCode) {
        if (responseConfig.isGzip()) {
            header.put("Content-Encoding", "gzip");
            header.remove("Content-Length");
        }

        header.put("Server", SERVER_INFO);
        if (!getHeader().containsKey("Connection")) {
            boolean keepAlive = request.getHeader("Connection") == null;
            if (keepAlive) {
                String httpVersion = request.getHttpVersion();
                if ("".equals(httpVersion.trim()) || httpVersion.equals("HTTP/1.0")) {
                    getHeader().put("Connection", "close");
                } else {
                    getHeader().put("Connection", "keep-alive");
                }
            } else if (!"close".equals(request.getHeader("Connection"))) {
                getHeader().put("Connection", "keep-alive");
            } else {
                getHeader().put("Connection", "close");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(StatusCodeUtil.getStatusCodeDesc(statusCode)).append(CRLF);
        for (Entry<String, String> he : header.entrySet()) {
            sb.append(he.getKey()).append(": ").append(he.getValue()).append(CRLF);
        }
        //deal cookie
        if (!responseConfig.isDisableCookie()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie != null && cookie.isCreate()) {
                        cookieList.add(cookie);
                    }
                }
            }
            for (Cookie cookie : cookieList) {
                sb.append("Set-Cookie: ").append(cookie).append(CRLF);
            }
        }
        sb.append(CRLF);
        return sb.toString().getBytes();
    }

    private byte[] wrapperResponseHeaderWithContentLength(Integer statusCode) {
        header.put("Transfer-Encoding", "chunked");
        return wrapperBaseResponseHeader(statusCode);
    }

    private byte[] tryConvertGzipBytes(byte[] bytes) {
        if (responseConfig.isGzip()) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
                GZIPOutputStream gzip = new GZIPOutputStream(bos);
                gzip.write(bytes);
                gzip.close();
                byte[] compressed = bos.toByteArray();
                bos.close();
                return compressed;
            } catch (IOException e) {
                throw new InternalException("convertGzipBytes error ", e);
            }

        }
        return bytes;
    }

    private void renderByStatusCode(Integer errorCode) {
        if (errorCode > 399) {
            header.put("Content-Type", "text/html");
            send(buildResponseData(errorCode, StringsUtil.getHtmlStrByStatusCode(errorCode).getBytes()));
        } else if (errorCode >= 300 && errorCode < 400) {
            if (!header.containsKey("Location")) {
                String welcomeFile = request.getServerConfig().getWelcomeFile();
                if (welcomeFile == null || "".equals(welcomeFile.trim())) {
                    header.put("Location", request.getScheme() + "://" + request.getHeader("Host") + "/" + request.getUri() + welcomeFile);
                }
            }
            send(buildResponseData(errorCode, new byte[]{}));
        }
    }

    @Override
    public void renderCode(int code) {
        renderByStatusCode(code);
    }

    @Override
    public void renderHtml(String htmlPath) {
        writeFile(new File(PathUtil.getStaticPath() + htmlPath));
    }

    @Override
    public void addCookie(Cookie cookie) {
        cookieList.add(cookie);
    }

    @Override
    public void renderHtmlStr(String htmlContent) {
        try {
            renderByMimeType("html", htmlContent.getBytes(responseConfig.getCharSet()));
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    private void renderByMimeType(String ext, byte[] body) {
        header.put("Content-Type", MimeTypeUtil.getMimeStrByExt(ext) + ";charset=" + responseConfig.getCharSet());
        send(buildResponseData(200, body));
    }

    @Override
    public void addHeader(String name, String value) {
        header.put(name, value);
    }

    @Override
    public void redirect(String url) {
        header.put("Location", url);
        send(buildResponseData(302, new byte[0]));
    }

    @Override
    public void forward(String uri) {
        redirect(request.getScheme() + "://" + request.getHeader("Host") + "/" + uri);
    }

    @Override
    public void renderFile(File file) {
        if (file.exists()) {
            header.put("Content-Disposition", "attachment;filename=\"" + file.getName() + "\"");
            writeFile(file);
        } else {
            renderCode(404);
        }
    }

    @Override
    public void renderFreeMarker(String name) {
        try {
            renderHtmlStr(FreeMarkerUtil.renderToFM(name, request));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            throw new InternalException(e);
        }
    }

    @Override
    public void write(InputStream inputStream) {
        write(inputStream, 200);
    }

    @Override
    public void write(InputStream inputStream, int code) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(wrapperResponseHeaderWithContentLength(code));
            send(byteArrayOutputStream, false);
            if (inputStream != null) {
                byte[] bytes = new byte[RESPONSE_BYTES_BLANK_SIZE];
                int length;
                if (responseConfig.isGzip()) {
                    inputStream = new GzipCompressingInputStream(inputStream);
                }
                while ((length = inputStream.read(bytes)) != -1) {
                    ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
                    ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
                    chunkedOutputStream.write(BytesUtil.subBytes(bytes, 0, length));
                    send(tmpOut, false);
                }
                ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
                ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
                chunkedOutputStream.close();
                send(tmpOut.toByteArray());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "", e);
                }
            }
        }
    }

    @Override
    public void write(ByteArrayOutputStream outputStream, int code) {
        send(buildResponseData(code, outputStream.toByteArray()));
    }

    public Map<String, String> getHeader() {
        return header;
    }

    @Override
    public void renderText(String text) {
        try {
            renderByMimeType("text", text.getBytes(responseConfig.getCharSet()));
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "", e);
            throw new InternalException(e);
        }
    }
}
