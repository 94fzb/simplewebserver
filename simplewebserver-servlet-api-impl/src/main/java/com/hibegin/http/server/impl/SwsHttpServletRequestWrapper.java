package com.hibegin.http.server.impl;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SwsHttpServletRequestWrapper extends SimpleHttpRequest {

    private final HttpServletRequest rawServletRequest;

    public SwsHttpServletRequestWrapper(HttpServletRequest rawServletRequest,
                                        RequestConfig requestConfig,
                                        ApplicationContext applicationContext) {
        super(null, applicationContext, requestConfig);
        this.rawServletRequest = rawServletRequest;
    }

    @Override
    public Map<String, String[]> getParamMap() {
        Map<String, String[]> parameterMap = rawServletRequest.getParameterMap();
        if (Objects.isNull(parameterMap)) {
            return Collections.emptyMap();
        }
        return parameterMap;
    }

    @Override
    public Map<String, String[]> decodeParamMap() {
        return getParamMap();
    }

    @Override
    public String getHeader(String key) {
        return getHeaderMap().get(key);
    }

    @Override
    public String getRemoteHost() {
        return rawServletRequest.getRemoteHost();
    }

    @Override
    public String getUri() {
        return rawServletRequest.getRequestURI().substring(rawServletRequest.getContextPath().length());
    }

    @Override
    public String getUrl() {
        return getFullUrl();
    }

    @Override
    public String getFullUrl() {
        return rawServletRequest.getRequestURL().toString();
    }

    @Override
    public String getRealPath() {
        return rawServletRequest.getServletContext().getRealPath("/");
    }

    @Override
    public String getQueryStr() {
        return rawServletRequest.getQueryString();
    }

    @Override
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(rawServletRequest.getMethod());
    }

    @Override
    public File getFile(String key) {
        if (Objects.isNull(files) || files.isEmpty()) {
            try {
                files = HttpRequestDecoderImpl.getFiles(getServerConfig(), IOUtil.getByteByInputStream(getInputStream()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getFile(key);
    }

    @Override
    public String getScheme() {
        return rawServletRequest.getScheme();
    }

    @Override
    public Map<String, String> getHeaderMap() {
        Enumeration<String> iterator = rawServletRequest.getHeaderNames();
        Map<String, String> headerMap = new TreeMap<>();
        while (iterator.hasMoreElements()) {
            String key = iterator.nextElement();
            headerMap.put(key, rawServletRequest.getHeader(key));
        }
        //放回实际应该要的
        headerMap.put("Cookie", ObjectUtil.requireNonNullElse(rawServletRequest.getHeader("Cookie"), ""));
        headerMap.put("Host", ObjectUtil.requireNonNullElse(rawServletRequest.getHeader("Host"), ""));
        return headerMap;
    }

    @Override
    public InputStream getInputStream() {
        try {
            return rawServletRequest.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ServerConfig getServerConfig() {
        ServerConfig serverConfig = super.getServerConfig();
        String[] serverInfo = rawServletRequest.getServletContext().getServerInfo().split("/");
        serverConfig.setApplicationName(serverInfo[0]);
        if (serverInfo.length > 1) {
            serverConfig.setApplicationVersion(serverInfo[1]);
        }
        return serverConfig;
    }

    @Override
    public String getContextPath() {
        return rawServletRequest.getServletContext().getContextPath();
    }

    @Override
    public HttpVersion getHttpVersion() {
        return HttpVersion.HTTP_1_1;
    }
}
