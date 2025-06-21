package com.hibegin.http.server.impl;

import com.hibegin.common.util.*;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.io.ChunkedOutputStream;
import com.hibegin.http.io.GzipCompressingInputStream;
import com.hibegin.http.io.KnownLengthStream;
import com.hibegin.http.io.LengthByteArrayInputStream;
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

    protected static final int RESPONSE_BYTES_BLANK_SIZE = 4096 * 256;
    private static final String CRLF = "\r\n";
    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpResponse.class);
    private static final List<String> textContentTypes = Arrays.asList(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-www-form-urlencoded",
            "application/vnd.api+json",
            "application/x-yaml"
    );
    protected final Map<String, String> header;
    protected final HttpRequest request;
    protected final List<Cookie> cookieList = new ArrayList<>();
    protected final ResponseConfig responseConfig;

    public SimpleHttpResponse(HttpRequest request, ResponseConfig responseConfig) {
        this.request = request;
        this.responseConfig = responseConfig;
        this.header = new LinkedHashMap<>(ObjectUtil.requireNonNullElse(responseConfig.getDefaultHeaders(), new LinkedHashMap<>()));
    }

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

    protected void send(byte[] bytes, boolean body, boolean close) {
        if (Objects.isNull(request.getHandler())) {
            if (!request.getServerConfig().isNativeImageAgent()) {
                if (EnvKit.isDebugMode()) {
                    LOGGER.warning("Request missing channel handler");
                }
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
        send(outputStream.toByteArray(), true, close);
    }

    protected void putHeader(String key, String value) {
        header.put(key, value);
    }

    protected void removeHeader(String key) {
        header.remove(key);
    }

    private void send(byte[] bytes) {
        send(bytes, true, "close".equalsIgnoreCase(getHeader().get("Connection")));
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

    protected byte[] wrapperBaseResponseHeader(int statusCode) {
        putHeader("Server", request.getServerConfig().getServerInfo());
        //1.0 不支持长连接
        if (request.getHttpVersion() == HttpVersion.HTTP_1_0) {
            putHeader("Connection", "close");
        } else {
            //如果业务没有设置该字段的情况
            if (!getHeader().containsKey("Connection")) {
                String requestConnection = request.getHeader("Connection");
                boolean keepAlive = Objects.isNull(requestConnection) || !"close".equalsIgnoreCase(requestConnection);
                if (keepAlive) {
                    putHeader("Connection", "keep-alive");
                } else {
                    putHeader("Connection", "close");
                }
            }
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
                putHeader("Set-Cookie", cookie.toString());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(request.getHttpVersion().getValue()).append(" ").append(statusCode).append(" ").append(StatusCodeUtil.getStatusCodeDesc(statusCode)).append(CRLF);
        for (Entry<String, String> he : header.entrySet()) {
            sb.append(he.getKey()).append(": ").append(he.getValue()).append(CRLF);
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
                    putHeader("Location", request.getScheme() + "://" + request.getHeader("Host") + "/" + request.getUri() + welcomeFile);
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
            putHeader("Content-Type", cType);
        }
    }

    private String getHeader(String key) {
        String headerValue = header.get(key);
        if (headerValue != null) {
            return headerValue;
        }
        for (Entry<String, String> entry : header.entrySet()) {
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
        putHeader(name, value);
    }

    @Override
    public void redirect(String url) {
        putHeader("Location", UrlEncodeUtils.encodeUrl(url));
        renderByMimeType("", null, 302);
    }

    @Override
    public void forward(String uri) {
        redirect(request.getScheme() + "://" + request.getHeader("Host") + "/" + uri);
    }

    @Override
    public void renderFile(File file) {
        if (file.exists()) {
            putHeader("Content-Disposition", "attachment;filename=\"" + file.getName() + "\"");
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
            renderHtmlStr(new BasicTemplateRender(request.getAttr(), ObjectUtil.requireNonNullElse(request.getServerConfig().getBasicTemplateClass(), SimpleHttpResponse.class)).renderByTemplateName(name));
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

    protected boolean needChunked(InputStream inputStream, long bodyLength) {
        if (inputStream == null) {
            return false;
        }
        if (bodyLength < 0) {
            String contentLength = getHeader("Content-Length");
            if (Objects.isNull(contentLength)) {
                return true;
            }
            return Integer.parseInt(contentLength) < 0;
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

    protected byte[] toChunkedBytes(byte[] inputBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(out);
        chunkedOutputStream.write(inputBytes);
        return out.toByteArray();
    }

    protected byte[] toCloseChunkedBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(out);
        chunkedOutputStream.close();
        return out.toByteArray();
    }

    private void write(InputStream inputStream, int code, long bodyLength) {
        try {
            //处理流，避免不传输实际的文件大小
            if (Objects.nonNull(inputStream) && inputStream instanceof FileInputStream) {
                FileInputStream fin = (FileInputStream) inputStream;
                bodyLength = fin.getChannel().size();
            } else if (Objects.nonNull(inputStream) && inputStream instanceof KnownLengthStream) {
                KnownLengthStream stream = (KnownLengthStream) inputStream;
                bodyLength = stream.getLength();
            }
            boolean chunked = needChunked(inputStream, bodyLength);
            if (chunked) {
                putHeader("Transfer-Encoding", "chunked");
                removeHeader("Content-Length");
                //启动gzip
                if (isGzip()) {
                    putHeader("Content-Encoding", "gzip");
                    inputStream = new GzipCompressingInputStream(inputStream);
                }
            } else {
                if (bodyLength >= 0) {
                    putHeader("Content-Length", bodyLength + "");
                }
            }
            send(wrapperBaseResponseHeader(code), false, false);
            if (inputStream == null) {
                send(new byte[0]);
                return;
            }
            byte[] bytes = new byte[RESPONSE_BYTES_BLANK_SIZE];
            int length;
            while ((length = inputStream.read(bytes)) != -1) {
                if (chunked) {
                    send(toChunkedBytes(BytesUtil.subBytes(bytes, 0, length)), true, false);
                } else {
                    send(BytesUtil.subBytes(bytes, 0, length), true, false);
                }
            }
            if (chunked) {
                send(toCloseChunkedBytes());
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
        write(new LengthByteArrayInputStream(outputStream.toByteArray()), code);
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
