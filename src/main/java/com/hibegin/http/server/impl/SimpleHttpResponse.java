package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.io.ChunkedOutputStream;
import com.hibegin.http.io.GzipCompressingInputStream;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.execption.InternalException;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.StatusCodeUtil;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.template.BasicTemplateRender;
import com.hibegin.template.FreemarkerTemplateRender;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpResponse implements HttpResponse {

    private static final String CRLF = "\r\n";
    private static final int RESPONSE_BYTES_BLANK_SIZE = 4096;
    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpResponse.class);
    private final Map<String, String> header = new TreeMap<>();
    private final HttpRequest request;
    private final List<Cookie> cookieList = new ArrayList<>();
    private final ResponseConfig responseConfig;

    public SimpleHttpResponse(HttpRequest request, ResponseConfig responseConfig) {
        this.request = request;
        this.responseConfig = responseConfig;
    }

    private static final Set<String> textContentTypes = Set.of(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-www-form-urlencoded",
            "application/vnd.api+json",
            "application/x-yaml"
    );


    private boolean isTextContent(String contentType) {
        return contentType.startsWith("text/") || textContentTypes.contains(contentType);
    }

    @Override
    public void writeFile(File file) {
        if (!file.exists()) {
            renderByStatusCode(404);
            return;
        }
        if (file.isDirectory()) {
            renderByStatusCode(302);
            return;
        }
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            // getMimeType
            trySetResponseContentType(MimeTypeUtil.getMimeStrByExt(ext));
            write(fileInputStream, 200, file.length());
        } catch (FileNotFoundException e) {
            renderByStatusCode(404);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    private void send(byte[] bytes, boolean close) {
        if (Objects.isNull(request.getHandler())) {
            if (!request.getServerConfig().isNativeImageAgent()) {
                LOGGER.warning("Request missing channel handler");
            }
            return;
        }
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
        header.put("Server", request.getServerConfig().getServerInfo());
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

    private String getHtmlStrByStatusCode(int statusCode) {
        return "<html><head><meta name=\"color-scheme\" content=\"light dark\"><title>" + statusCode + " " + StatusCodeUtil.getStatusCodeDesc(statusCode) + "</title></head><body><center><h1>" + statusCode + " " + StatusCodeUtil.getStatusCodeDesc(statusCode) + "</h1></center><hr><center>" + request.getServerConfig().getServerInfo() + "</center></body></html>";
    }

    private void renderByStatusCode(int errorCode) {
        if (errorCode > 399) {
            renderByMimeType("html", getHtmlStrByStatusCode(errorCode).getBytes(), errorCode);
        } else if (errorCode > 299) {
            if (!header.containsKey("Location")) {
                String welcomeFile = request.getServerConfig().getWelcomeFile();
                if (welcomeFile == null || "".equals(welcomeFile.trim())) {
                    header.put("Location", request.getScheme() + "://" + request.getHeader("Host") + "/" + request.getUri() + welcomeFile);
                }
            }
            renderByMimeType("", null, errorCode);
        }
    }

    @Override
    public void renderCode(int code) {
        renderByStatusCode(code);
    }

    @Override
    public void renderHtml(String htmlPath) {
        writeFile(PathUtil.getStaticFile(htmlPath));
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
        if (ext != null && !ext.isEmpty()) {
            trySetResponseContentType(MimeTypeUtil.getMimeStrByExt(ext));
        }
        if (body != null && body.length > 0) {
            write(new ByteArrayInputStream(body), code, body.length);
        } else {
            write(null, code, -1);
        }
    }

    private void trySetResponseContentType(String contentType) {
        if (getHeader("Content-Type") == null) {
            String cType = contentType;
            if (!cType.contains(";") && isTextContent(cType)) {
                cType = contentType + ";charset=" + responseConfig.getCharSet();
            }
            header.put("Content-Type", cType);
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
        if (name.equalsIgnoreCase("Content-type")) {
            trySetResponseContentType(value);
            return;
        }
        header.put(name, value);
    }

    @Override
    public void redirect(String url) {
        header.put("Location", url);
        renderByMimeType("", null, 302);
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
            renderHtmlStr(new FreemarkerTemplateRender(request).renderByTemplateName(name));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
            throw new InternalException(e);
        }
    }

    @Override
    public void renderBasicTemplate(String name) {
        try {
            renderHtmlStr(new BasicTemplateRender(request.getAttr(), Objects.requireNonNullElse(request.getServerConfig().getBasicTemplateClass(), SimpleHttpResponse.class)).renderByTemplateName(name));
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

    private boolean needChunked(InputStream inputStream, long bodyLength) {
        if (inputStream == null) {
            return false;
        }
        if (bodyLength < 0) {
            return true;
        }
        if (bodyLength == 0) {
            return false;
        }
        return isGzip();
    }

    private boolean isGzip() {
        if (!responseConfig.isEnableGzip()) {
            return false;
        }
        String requestHeader = request.getHeader("Accept-Encoding");
        if (Objects.isNull(requestHeader) || requestHeader.trim().isEmpty()) {
            return false;
        }
        if (!requestHeader.contains("gzip")) {
            return false;
        }
        String contentType = getHeader("Content-Type");
        if (Objects.isNull(contentType) || contentType.trim().isEmpty()) {
            return false;
        }
        return responseConfig.getGzipMimeTypes().stream().anyMatch(contentType::contains);
    }

    private byte[] toChunked(byte[] inputBytes) throws IOException {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
        chunkedOutputStream.write(inputBytes);
        return tmpOut.toByteArray();
    }

    private void write(InputStream inputStream, int code, long bodyLength) {
        try {
            boolean chunked = needChunked(inputStream, bodyLength);
            if (chunked) {
                header.put("Transfer-Encoding", "chunked");
                header.remove("Content-Length");
                //启动gzip
                if (isGzip()) {
                    header.put("Content-Encoding", "gzip");
                    inputStream = new GzipCompressingInputStream(inputStream);
                }
            } else {
                header.put("Content-Length", Math.max(bodyLength, 0) + "");
            }
            send(wrapperBaseResponseHeader(code), false);
            if (inputStream == null) {
                send(new byte[0]);
                return;
            }
            byte[] bytes = new byte[RESPONSE_BYTES_BLANK_SIZE];
            int length;
            while ((length = inputStream.read(bytes)) != -1) {
                if (chunked) {
                    send(toChunked(BytesUtil.subBytes(bytes, 0, length)), false);
                } else {
                    send(BytesUtil.subBytes(bytes, 0, length), false);
                }
            }
            if (chunked) {
                ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
                ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
                chunkedOutputStream.close();
                send(tmpOut.toByteArray());
            } else {
                send(new byte[0]);
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
