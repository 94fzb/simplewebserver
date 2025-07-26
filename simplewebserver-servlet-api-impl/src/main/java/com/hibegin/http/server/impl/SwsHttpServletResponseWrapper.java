package com.hibegin.http.server.impl;


import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;

public class SwsHttpServletResponseWrapper extends SimpleHttpResponse {

    private final HttpServletResponse rawServletResponse;
    private final OutputStream outputStream;

    public SwsHttpServletResponseWrapper(HttpRequest request, ResponseConfig responseConfig, HttpServletResponse rawServletResponse) {
        super(request, responseConfig);
        this.rawServletResponse = rawServletResponse;
        Collection<String> headerNames = rawServletResponse.getHeaderNames();
        for (String headerName : headerNames) {
            header.put(headerName, rawServletResponse.getHeader(headerName));
        }
        try {
            this.outputStream = rawServletResponse.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void putHeader(String key, String value) {
        if (Objects.equals("Transfer-Encoding", key)) {
            return;
        }
        if (Objects.equals(key, "Server")) {
            return;
        }
        if (Objects.equals(key, "Connection")) {
            return;
        }
        super.putHeader(key, value);
        rawServletResponse.addHeader(key, value);
    }

    @Override
    protected boolean needChunked(InputStream inputStream, long bodyLength) {
        return false;
    }

    @Override
    protected void removeHeader(String key) {
        if (Objects.equals(key, "Content-Length")) {
            rawServletResponse.setHeader("Content-Length", null);
        }
        super.removeHeader(key);
    }

    @Override
    protected byte[] wrapperBaseResponseHeader(int statusCode) {
        rawServletResponse.setStatus(statusCode);
        return super.wrapperBaseResponseHeader(statusCode);
    }

    @Override
    protected void send(byte[] bytes, boolean body, boolean close) {
        if (body) {
            try {
                outputStream.write(bytes);
                if (close) {
                    outputStream.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
