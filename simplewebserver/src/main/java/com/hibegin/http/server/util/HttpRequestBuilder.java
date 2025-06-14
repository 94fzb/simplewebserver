package com.hibegin.http.server.util;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class HttpRequestBuilder {

    public static HttpRequest buildRequest(HttpMethod method, String uri, String host, String userAgent, RequestConfig requestConfig, ApplicationContext applicationContext) throws Exception {
        String httpHeader = method.toString() + " " + uri + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "X-Real-IP: 127.0.0.1\r\n" +
                "User-Agent: " + userAgent + "\r\n" +
                "\r\n";
        HttpRequestDecoderImpl httpRequestDecoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, null);
        httpRequestDecoder.doDecode(ByteBuffer.wrap(httpHeader.getBytes(Charset.defaultCharset())));
        return httpRequestDecoder.getRequest();
    }
}
