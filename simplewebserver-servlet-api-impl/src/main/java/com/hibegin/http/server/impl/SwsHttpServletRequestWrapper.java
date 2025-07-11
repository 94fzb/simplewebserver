package com.hibegin.http.server.impl;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.UrlDecodeUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SwsHttpServletRequestWrapper extends SimpleHttpRequest {

    private final String contextPath;
    private final String realPath;
    private final String scheme;

    public SwsHttpServletRequestWrapper(HttpServletRequest rawServletRequest,
                                        RequestConfig requestConfig,
                                        ApplicationContext applicationContext) {
        super(null, applicationContext, requestConfig);
        String[] serverInfo = rawServletRequest.getServletContext().getServerInfo().split("/");
        //Update runtime info
        ServerConfig serverConfig = super.getServerConfig();
        serverConfig.setApplicationName(serverInfo[0]);
        if (serverInfo.length > 1) {
            serverConfig.setApplicationVersion(serverInfo[1]);
        }
        serverConfig.setPort(rawServletRequest.getLocalPort());
        this.paramMap = _getParamMap(rawServletRequest);
        this.method = HttpMethod.valueOf(rawServletRequest.getMethod());
        this.contextPath = rawServletRequest.getContextPath();
        this.paramMap = _getParamMap(rawServletRequest);
        this.header = _getHeaderMap(rawServletRequest);
        this.uri = UrlDecodeUtils.decodePath(new String(rawServletRequest.getRequestURI().getBytes(StandardCharsets.ISO_8859_1)), requestConfig.getCharSet());
        this.realPath = rawServletRequest.getServletContext().getRealPath("/");
        this.queryStr = rawServletRequest.getQueryString();
        this.scheme = rawServletRequest.getScheme();
        try {
            this.inputStream = rawServletRequest.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String[]> _getParamMap(HttpServletRequest rawServletRequest) {
        Map<String, String[]> parameterMap = rawServletRequest.getParameterMap();
        if (Objects.isNull(parameterMap)) {
            return Collections.emptyMap();
        }
        Map<String, String[]> decodedParameterMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = new String[entry.getValue().length];
            for (int i = 0; i < values.length; i++) {
                values[i] = decodeRequestParam(entry.getValue()[i]);
            }
            decodedParameterMap.put(entry.getKey(), values);
        }
        return decodedParameterMap;
    }

    /**
     * 用于转化HTTP的中文乱码
     */
    private static String decodeRequestParam(String value) {
        if (value == null) {
            return "";
        }
        /*//如果可以正常读取到中文的情况，直接跳过转换
        if (containsHanScript(param)) {
            return param;
        }*/
        try {
            return URLDecoder.decode(new String(value.getBytes(StandardCharsets.ISO_8859_1)), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getRealPath() {
        return realPath;
    }

    @Override
    public File getFile(String key) {
        if (Objects.isNull(files) || files.isEmpty()) {
            try {
                files = HttpRequestDecoderImpl.getFiles(getServerConfig(), IOUtil.getByteByInputStream(inputStream));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getFile(key);
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    private Map<String, String> _getHeaderMap(HttpServletRequest rawServletRequest) {
        Enumeration<String> iterator = rawServletRequest.getHeaderNames();
        Map<String, String> headerMap = new LinkedHashMap<>();
        while (iterator.hasMoreElements()) {
            String key = iterator.nextElement();
            headerMap.put(key, rawServletRequest.getHeader(key));
        }
        return headerMap;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public HttpVersion getHttpVersion() {
        return HttpVersion.HTTP_1_1;
    }
}
