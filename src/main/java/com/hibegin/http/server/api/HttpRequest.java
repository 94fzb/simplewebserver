package com.hibegin.http.server.api;

import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.impl.ServerContext;
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

    boolean getParaToBool(String key);

    String getParaToStr(String key);

    int getParaToInt(String key);

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

    ServerConfig getServerConfig();

    ServerContext getServerContext();
}
