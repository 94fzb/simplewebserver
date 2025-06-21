package com.hibegin.http.server.api;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.web.session.HttpSession;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public interface HttpRequest {

    Map<String, String[]> getParamMap();

    Map<String, String[]> decodeParamMap();

    String getHeader(String key);

    String getRemoteHost();

    String getUri();

    String getUrl();

    String getFullUrl();

    String getRealPath();

    String getQueryStr();

    HttpMethod getMethod();

    Cookie[] getCookies();

    HttpSession getSession();

    Boolean getParaToBool(String key);

    default Boolean getParaToBool(String key, Boolean defaultValue) {
        return ObjectUtil.requireNonNullElse(getParaToBool(key), defaultValue);
    }

    String getParaToStr(String key);

    default String getParaToStr(String key, String defaultValue) {
        return ObjectUtil.requireNonNullElse(getParaToStr(key), defaultValue);
    }

    Integer getParaToInt(String key);

    default Integer getParaToInt(String key, int defaultValue) {
        return ObjectUtil.requireNonNullElse(getParaToInt(key), defaultValue);

    }

    File getFile(String key);

    Map<String, Object> getAttr();

    String getScheme();

    Map<String, String> getHeaderMap();

    InputStream getInputStream();

    RequestConfig getRequestConfig();

    ReadWriteSelectorHandler getHandler();

    long getCreateTime();

    ByteBuffer getInputByteBuffer();

    ByteBuffer getRequestBodyByteBuffer();

    ByteBuffer getRequestBodyByteBuffer(int offset);

    ServerConfig getServerConfig();

    ApplicationContext getApplicationContext();

    HttpVersion getHttpVersion();

    default String getContextPath() {
        return ObjectUtil.requireNonNullElse(getServerConfig().getContextPath(), "");
    }
}
