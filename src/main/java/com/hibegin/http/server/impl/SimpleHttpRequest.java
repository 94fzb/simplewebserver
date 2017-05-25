package com.hibegin.http.server.impl;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.web.session.HttpSession;
import com.hibegin.http.server.web.session.SessionUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpRequest implements HttpRequest {

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpRequest.class);
    protected Map<String, String> header = new HashMap<>();
    protected Map<String, String[]> paramMap;
    protected String uri;
    protected String queryStr;
    protected HttpMethod method;
    protected Map<String, File> files = new HashMap<>();
    protected ByteBuffer requestBodyBuffer;
    protected String requestHeaderStr;
    private Cookie[] cookies;
    private HttpSession session;
    private RequestConfig requestConfig;
    private String scheme = "http";
    private ServerContext serverContext;
    private Map<String, Object> attr = Collections.synchronizedMap(new HashMap<String, Object>());
    private ReadWriteSelectorHandler handler;
    private long createTime;
    private InputStream inputStream;

    protected SimpleHttpRequest(ReadWriteSelectorHandler handler, ServerContext serverContext, RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
        this.createTime = System.currentTimeMillis();
        this.handler = handler;
        this.serverContext = serverContext;
        if (this.requestConfig.isSsl()) {
            scheme = "https";
        }
    }

    @Override
    public Map<String, String[]> getParamMap() {
        return paramMap;
    }

    @Override
    public String getHeader(String key) {
        return header.get(key);
    }

    @Override
    public String getRemoteHost() {
        return ((InetSocketAddress) handler.getChannel().socket().getRemoteSocketAddress()).getHostString();
    }

    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public String getUrl() {
        return scheme + "://" + header.get("Host") + uri;
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
        if (!requestConfig.isDisableCookie()) {
            String cookieHeader = header.get("Cookie");
            if (cookieHeader != null) {
                cookies = Cookie.saxToCookie(cookieHeader);
                String jsessionid = Cookie.getJSessionId(cookieHeader);
                if (jsessionid != null) {
                    session = SessionUtil.getSessionById(jsessionid);
                }
            }
            if (create && session == null) {
                if (cookies == null) {
                    cookies = new Cookie[1];
                } else {
                    cookies = new Cookie[cookies.length + 1];
                }
                Cookie cookie = new Cookie(true);
                String jsessionid = UUID.randomUUID().toString();
                cookie.setName(Cookie.JSESSIONID);
                cookie.setPath("/");
                cookie.setValue(jsessionid);
                cookies[cookies.length - 1] = cookie;
                session = new HttpSession(jsessionid);
                SessionUtil.sessionMap.put(jsessionid, session);
                LOGGER.info("create a cookie " + cookie.toString());
            }
        }
    }

    @Override
    public String getParaToStr(String key) {
        if (paramMap.get(key) != null) {
            try {
                return URLDecoder.decode(paramMap.get(key)[0], requestConfig.getCharSet());
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        return null;
    }

    @Override
    public File getFile(String key) {
        return files.get(key);
    }

    @Override
    public int getParaToInt(String key) {
        if (paramMap.get(key) != null) {
            return Integer.parseInt(paramMap.get(key)[0]);
        }
        return 0;
    }

    @Override
    public boolean getParaToBool(String key) {
        return paramMap.get(key) != null && "on".equals(paramMap.get(key)[0]);
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getFullUrl() {
        if (queryStr != null) {
            return getUrl() + "?" + queryStr;
        }
        return getUrl();
    }

    @Override
    public String getQueryStr() {
        return queryStr;
    }

    @Override
    public Map<String, Object> getAttr() {
        return attr;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public Map<String, String> getHeaderMap() {
        return header;
    }

    @Override
    public InputStream getInputStream() {
        if (inputStream != null) {
            return inputStream;
        } else {
            if (requestBodyBuffer != null) {
                inputStream = new ByteArrayInputStream(requestBodyBuffer.array());
            } else {
                inputStream = new ByteArrayInputStream(new byte[]{});
            }
            return inputStream;
        }
    }

    @Override
    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    public Map<String, String[]> decodeParamMap() {
        Map<String, String[]> encodeMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : getParamMap().entrySet()) {
            String[] strings = new String[entry.getValue().length];
            for (int i = 0; i < entry.getValue().length; i++) {
                try {
                    strings[i] = URLDecoder.decode(entry.getValue()[i], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    LOGGER.log(Level.SEVERE, "decode error", e);
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

    public long getCreateTime() {
        return createTime;
    }

    public ByteBuffer getInputByteBuffer() {
        byte[] splitBytes = HttpRequestDecoderImpl.SPLIT.getBytes();
        byte[] bytes = requestHeaderStr.getBytes();
        if (requestBodyBuffer == null) {
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + splitBytes.length);
            buffer.put(bytes);
            buffer.put(splitBytes);
            return buffer;
        } else {
            byte[] dataBytes = requestBodyBuffer.array();
            ByteBuffer buffer = ByteBuffer.allocate(requestHeaderStr.getBytes().length + splitBytes.length + dataBytes.length);
            buffer.put(requestHeaderStr.getBytes());
            buffer.put(splitBytes);
            buffer.put(dataBytes);
            return buffer;
        }
    }

    @Override
    public ServerConfig getServerConfig() {
        return getServerContext().getServerConfig();
    }

    @Override
    public ServerContext getServerContext() {
        return serverContext;
    }

    @Override
    public ByteBuffer getRequestBodyByteBuffer() {
        return requestBodyBuffer;
    }

    public void deleteTempUploadFiles() {
        for (File file : files.values()) {
            file.delete();
        }
    }
}
