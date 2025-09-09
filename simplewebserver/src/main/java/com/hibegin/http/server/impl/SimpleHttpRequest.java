package com.hibegin.http.server.impl;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.web.session.HttpSession;
import com.hibegin.http.server.web.session.SessionUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpRequest extends BaseLockObject implements HttpRequest {

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpRequest.class);
    private final RequestConfig requestConfig;
    private final ApplicationContext applicationContext;
    private final ReadWriteSelectorHandler handler;
    protected long createTime;
    protected Map<String, String> header = new LinkedHashMap<>();
    protected Map<String, String[]> paramMap;
    protected String uri;
    protected String queryStr;
    protected HttpMethod method;
    protected Map<String, File> files;
    protected File tmpRequestBodyFile;
    protected String requestHeaderStr;
    protected InputStream inputStream;
    private Cookie[] cookies;
    private HttpSession session;
    private Map<String, Object> attr;
    protected String contextPath;


    protected SimpleHttpRequest(ReadWriteSelectorHandler handler, ApplicationContext applicationContext, RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
        this.createTime = System.currentTimeMillis();
        this.handler = handler;
        this.applicationContext = applicationContext;
        this.contextPath = applicationContext.getServerConfig().getContextPath();
    }

    @Override
    public Map<String, String[]> getParamMap() {
        return paramMap;
    }

    @Override
    public String getHeader(String key) {
        Map<String, String> map = getHeaderMap();
        String headerValue = map.get(key);
        if (headerValue != null) {
            return headerValue;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public String getRemoteHost() {
        if (Objects.isNull(handler) || Objects.isNull(handler.getChannel()) || Objects.isNull(handler.getChannel().socket())) {
            return null;
        }
        return ((InetSocketAddress) handler.getChannel().socket().getRemoteSocketAddress()).getHostString();
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public String getUrl() {
        return getScheme() + "://" + getHeader("Host") + getContextPath() + getUri();
    }

    @Override
    public String getRealPath() {
        return PathUtil.getStaticPath();
    }

    @Override
    public Cookie[] getCookies() {
        if (cookies == null) {
            dealWithCookie(false);
        }
        if (cookies == null) {
            //avoid not happen NullPointException
            cookies = new Cookie[0];
        }
        return cookies;
    }

    @Override
    public HttpSession getSession() {
        if (session == null) {
            dealWithCookie(true);
        }
        return session;
    }

    private void dealWithCookie(boolean create) {
        String cookieStr = ObjectUtil.requireNonNullElse(getHeader("Cookie"), "");
        cookies = Cookie.saxToCookie(cookieStr);
        if (requestConfig.isDisableSession()) {
            return;
        }
        String sessionValue = Cookie.getJSessionId(cookieStr, getServerConfig().getSessionId());
        if (sessionValue != null) {
            session = SessionUtil.getSessionById(sessionValue);
        }
        if (Objects.nonNull(session)) {
            return;
        }
        if (!create) {
            return;
        }
        if (cookies == null) {
            cookies = new Cookie[1];
        } else {
            cookies = new Cookie[cookies.length + 1];
        }
        Cookie cookie = buildSessionCookie(sessionValue);
        cookies[cookies.length - 1] = cookie;
        session = new HttpSession(cookie.getValue());
        SessionUtil.sessionMap.put(cookie.getValue(), session);
    }

    private Cookie buildSessionCookie(String sessionValue) {
        Cookie cookie = new Cookie(true);
        if (Objects.isNull(sessionValue) || sessionValue.trim().isEmpty()) {
            cookie.setValue(UUID.randomUUID().toString());
        } else {
            cookie.setValue(sessionValue);
        }
        cookie.setName(getServerConfig().getSessionId());
        cookie.setPath("/");
        return cookie;
    }

    @Override
    public String getParaToStr(String key) {
        Map<String, String[]> map = getParamMap();
        if (map.get(key) != null) {
            try {
                return URLDecoder.decode(map.get(key)[0], requestConfig.getCharSet());
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        return null;
    }

    @Override
    public File getFile(String key) {
        if (files != null) {
            return files.get(key);
        }
        return null;
    }

    @Override
    public Integer getParaToInt(String key) {
        Map<String, String[]> param = getParamMap();
        String[] values = param.get(key);
        if (Objects.nonNull(values) && values.length > 0) {
            return Integer.parseInt(values[0]);
        }
        return 0;
    }

    @Override
    public Boolean getParaToBool(String key) {
        Map<String, String[]> _paramMap = getParamMap();
        return _paramMap.get(key) != null && ("on".equals(_paramMap.get(key)[0]) || "true".equals(_paramMap.get(key)[0]));
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getFullUrl() {
        if (queryStr == null || queryStr.isEmpty()) {
            return getUrl();
        }
        return getUrl() + "?" + queryStr;
    }

    @Override
    public String getQueryStr() {
        return queryStr;
    }

    @Override
    public Map<String, Object> getAttr() {
        if (attr == null) {
            attr = Collections.synchronizedMap(new HashMap<>());
        }
        return attr;
    }

    @Override
    public String getScheme() {
        String proto = getHeader("X-Forwarded-Proto");
        if (Objects.nonNull(proto)) {
            return proto;
        }
        if (requestConfig.isSsl()) {
            return "https";
        }
        if (Objects.isNull(handler)) {
            return "http";
        }
        return handler.isPlain() ? "http" : "https";
    }

    @Override
    public Map<String, String> getHeaderMap() {
        return header;
    }

    @Override
    public InputStream getInputStream() {
        if (inputStream != null) {
            return inputStream;
        }
        if (tmpRequestBodyFile != null) {
            try {
                inputStream = new FileInputStream(tmpRequestBodyFile);
            } catch (FileNotFoundException e) {
                //e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            inputStream = new ByteArrayInputStream(new byte[]{});
        }
        return inputStream;
    }

    @Override
    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    @Override
    public Map<String, String[]> decodeParamMap() {
        Map<String, String[]> encodeMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : getParamMap().entrySet()) {
            String[] strings = new String[entry.getValue().length];
            for (int i = 0; i < entry.getValue().length; i++) {
                try {
                    strings[i] = URLDecoder.decode(entry.getValue()[i], StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
            encodeMap.put(entry.getKey(), strings);
        }
        return encodeMap;
    }

    @Override
    public ReadWriteSelectorHandler getHandler() {
        return handler;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public ByteBuffer getInputByteBuffer() {
        byte[] splitBytes = HttpRequestDecoderImpl.SPLIT.getBytes();
        byte[] headerBytes = requestHeaderStr.getBytes();
        byte[] bodyBytes = getRequestBodyByteBuffer().array();
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + splitBytes.length + bodyBytes.length);
        buffer.put(headerBytes);
        buffer.put(splitBytes);
        buffer.put(bodyBytes);
        return buffer;
    }

    @Override
    public ServerConfig getServerConfig() {
        return getApplicationContext().getServerConfig();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public ByteBuffer getRequestBodyByteBuffer() {
        return getRequestBodyByteBuffer(0);
    }

    @Override
    public ByteBuffer getRequestBodyByteBuffer(int offset) {
        lock.lock();
        try {
            if (tmpRequestBodyFile != null && offset < tmpRequestBodyFile.length()) {
                FileInputStream fileInputStream = new FileInputStream(tmpRequestBodyFile.toString());
                fileInputStream.skip(offset);
                return ByteBuffer.wrap(IOUtil.getByteByInputStream(fileInputStream));
            } else {
                return ByteBuffer.wrap(new byte[0]);
            }
        } catch (Exception e) {
            if (EnvKit.isDevMode()) {
                LOGGER.warning("Load requestBody error " + e.getMessage());
            }
            return ByteBuffer.wrap(new byte[0]);
        } finally {
            lock.unlock();
        }
    }

    public void deleteTempUploadFiles() {
        lock.lock();
        try {
            if (tmpRequestBodyFile != null) {
                FileCacheKit.deleteCache(tmpRequestBodyFile);
                tmpRequestBodyFile = null;
            }
            if (files != null) {
                for (File file : files.values()) {
                    FileCacheKit.deleteCache(file);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getContextPath() {
        return ObjectUtil.requireNonNullElse(contextPath, "");
    }

    @Override
    public HttpVersion getHttpVersion() {
        if (requestHeaderStr != null) {
            String[] tempArr = requestHeaderStr.split("\r\n");
            if (tempArr.length > 0) {
                if (tempArr[0].split(" ").length > 2) {
                    String version = tempArr[0].split(" ")[2];
                    if (Objects.equals(version, HttpVersion.HTTP_1_0.getValue())) {
                        return HttpVersion.HTTP_1_0;
                    }
                    if (Objects.equals(version, HttpVersion.HTTP_1_1.getValue())) {
                        return HttpVersion.HTTP_1_1;
                    }
                }
            }
        }
        return HttpVersion.HTTP_1_0;
    }
}
