package com.hibegin.http.server.impl;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.web.session.HttpSession;
import com.hibegin.http.server.util.PathUtil;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpRequest implements HttpRequest {

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpRequest.class);
    protected SocketAddress ipAddr;
    protected Map<String, String> header = new HashMap<String, String>();
    protected Map<String, String[]> paramMap;
    protected String uri;
    protected String queryStr;
    protected HttpMethod method;
    protected Cookie[] cookies;
    protected HttpSession session;
    protected Map<String, File> files = new HashMap<String, File>();
    protected ByteBuffer dataBuffer;
    protected String scheme = "http";
    protected RequestConfig requestConfig;
    protected StringBuilder headerSb = new StringBuilder();
    private ServerContext serverContext;
    private Map<String, Object> attr = new ConcurrentHashMap<>();
    private ReadWriteSelectorHandler handler;
    private long createTime;

    protected SimpleHttpRequest(long createTime, ReadWriteSelectorHandler handler, ServerContext serverContext) {
        this.handler = handler;
        this.createTime = createTime;
        this.serverContext = serverContext;
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
        return ((InetSocketAddress) ipAddr).getHostString();
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
        return cookies;
    }

    @Override
    public HttpSession getSession() {
        return session;
    }


    @Override
    public String getParaToStr(String key) {
        if (paramMap.get(key) != null) {
            try {
                return URLDecoder.decode(paramMap.get(key)[0], "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
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
    public byte[] getContentByte() {
        if (dataBuffer != null) {
            return dataBuffer.array();
        } else {
            return new byte[]{};
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
        byte[] bytes = headerSb.toString().getBytes();
        if (dataBuffer == null) {
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
            buffer.put(bytes);
            return buffer;
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + dataBuffer.array().length);
            buffer.put(bytes);
            buffer.put(dataBuffer.array());
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
}
