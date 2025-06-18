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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SwsHttpServletRequestWrapper extends SimpleHttpRequest {

    private final HttpServletRequest rawServletRequest;

    public SwsHttpServletRequestWrapper(HttpServletRequest rawServletRequest,
                                        RequestConfig requestConfig,
                                        ApplicationContext applicationContext) {
        super(null, applicationContext, requestConfig);
        this.rawServletRequest = rawServletRequest;
        String[] serverInfo = rawServletRequest.getServletContext().getServerInfo().split("/");
        //Update runtime info
        ServerConfig serverConfig = super.getServerConfig();
        serverConfig.setApplicationName(serverInfo[0]);
        if (serverInfo.length > 1) {
            serverConfig.setApplicationVersion(serverInfo[1]);
        }
        serverConfig.setPort(rawServletRequest.getLocalPort());
    }

    @Override
    public Map<String, String[]> getParamMap() {
        Map<String, String[]> parameterMap = rawServletRequest.getParameterMap();
        if (Objects.isNull(parameterMap)) {
            return Collections.emptyMap();
        }
        Map<String, String[]> decodedParameterMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = new String[entry.getValue().length];
            for (int i = 0; i < values.length; i++) {
                values[i] = convertRequestParam(entry.getValue()[i]);
            }
            decodedParameterMap.put(entry.getKey(), values);
        }
        return decodedParameterMap;
    }


    private static boolean containsHanScript(String s) {
        return s.codePoints().anyMatch(codepoint -> Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN);
    }

    /**
     * 用于转化 GET 的中文乱码
     */
    public static String convertRequestParam(String param) {
        if (param == null) {
            return "";
        }
        //如果可以正常读取到中文的情况，直接跳过转换
        if (containsHanScript(param)) {
            return param;
        }
        try {
            return URLDecoder.decode(new String(param.getBytes(StandardCharsets.ISO_8859_1)), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
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
    public String getContextPath() {
        return rawServletRequest.getServletContext().getContextPath();
    }

    @Override
    public HttpVersion getHttpVersion() {
        return HttpVersion.HTTP_1_1;
    }
}
