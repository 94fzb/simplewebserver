package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
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

public class SimpleHttpResponse implements HttpResponse {

    private static final String CRLF = "\r\n";
    private static final int RESPONSE_BYTES_BLANK_SIZE = 4096;
    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpResponse.class);
    private final Map<String, String> header = new HashMap<>();
    private final HttpRequest request;
    private final List<Cookie> cookieList = new ArrayList<>();
    private final ResponseConfig responseConfig;
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
                trySetResponseContentType(MimeTypeUtil.getMimeStrByExt(ext));
                write(fileInputStream, 200, file.length());
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
            request.getHandler().close();
            //LOGGER.log(Level.WARNING, "send error " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "send error", e);
            throw new InternalException("send error", e);
        }
    }

    @Override
    public void send(ByteArrayOutputStream outputStream, boolean close) {
        send(outputStream.toByteArray(), close);
    }


    private void send(byte[] bytes) {
        send(bytes, "close".equalsIgnoreCase(getHeader().get("Connection")));
    }

    @Override
    public void renderJson(Object obj) {
        try {
            renderByMimeType("json", request.getServerConfig().getHttpJsonMessageConverter().toJson(obj).getBytes());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            throw new InternalException(e);
        }
    }

    private byte[] wrapperBaseResponseHeader(int statusCode) {
        header.put("Server", SERVER_INFO);
        if (!getHeader().containsKey("Connection")) {
            boolean keepAlive = request.getHeader("Connection") == null;
            if (keepAlive) {
                String httpVersion = request.getHttpVersion();
                if ("".equals(httpVersion.trim()) || "HTTP/1.0".equals(httpVersion)) {
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

    private void renderByStatusCode(Integer errorCode) {
        if (errorCode > 399) {
            renderByMimeType("html", StringsUtil.getHtmlStrByStatusCode(errorCode).getBytes(), errorCode);
        } else if (errorCode > 299) {
            if (!header.containsKey("Location")) {
                String welcomeFile = request.getServerConfig().getWelcomeFile();
                if (welcomeFile == null || "".equals(welcomeFile.trim())) {
                    header.put("Location", request.getScheme() + "://" + request.getHeader("Host") + "/" + request.getUri() + welcomeFile);
                }
            }
            renderByMimeType("", new byte[0], errorCode);
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
        renderByMimeType(ext, body, 200);
    }

    private void renderByMimeType(String ext, byte[] body, int code) {
        if (ext != null && ext.length() > 0) {
            trySetResponseContentType(MimeTypeUtil.getMimeStrByExt(ext) + ";charset=" + responseConfig.getCharSet());
        }
        write(new ByteArrayInputStream(body), code, body.length);
    }

    private void trySetResponseContentType(String contentType) {
        if (getHeader("Content-Type") == null) {
            header.put("Content-Type", contentType);
        }
    }

    private String getHeader(String key) {
        String headerValue = header.get(key);
        if (headerValue != null) {
            return headerValue;
        }
        for (Map.Entry<String, String> entry : header.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void addHeader(String name, String value) {
        header.put(name, value);
    }

    @Override
    public void redirect(String url) {
        header.put("Location", url);
        renderByMimeType("", new byte[0], 302);
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
        write(inputStream, code, -1);
    }

    private boolean needChunked(long bodyLength) {
        if (bodyLength == 0) {
            return false;
        }
        if (bodyLength < 0) {
            return true;
        }
        return responseConfig.isGzip();
    }

    private byte[] toChunked(byte[] inputBytes) throws IOException {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
        chunkedOutputStream.write(inputBytes);
        return tmpOut.toByteArray();
    }

    private void write(InputStream inputStream, int code, long bodyLength) {
        try {
            if (needChunked(bodyLength)) {
                header.put("Transfer-Encoding", "chunked");
                header.remove("Content-Length");
            } else {
                header.put("Content-Length", bodyLength + "");
            }
            if (responseConfig.isGzip()) {
                header.put("Content-Encoding", "gzip");
                if (inputStream != null) {
                    inputStream = new GzipCompressingInputStream(inputStream);
                }
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(wrapperBaseResponseHeader(code));
            send(byteArrayOutputStream, false);
            if (inputStream == null) {
                return;
            }
            byte[] bytes = new byte[RESPONSE_BYTES_BLANK_SIZE];
            int length;
            while ((length = inputStream.read(bytes)) != -1) {
                if (needChunked(bodyLength)) {
                    send(toChunked(BytesUtil.subBytes(bytes, 0, length)), false);
                } else {
                    send(BytesUtil.subBytes(bytes, 0, length), false);
                }
            }
            if (needChunked(bodyLength)) {
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
        write(new ByteArrayInputStream(outputStream.toByteArray()), code);
    }

    @Override
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
