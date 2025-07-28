package com.hibegin.http.server.util;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequestBuilder {

    public static HttpRequest buildRequest(HttpMethod method, String uri, String host, String userAgent, RequestConfig requestConfig, ApplicationContext applicationContext) throws Exception {
        StringBuilder httpStr = new StringBuilder(method.toString() + " " + uri + " HTTP/1.1\r\n");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        headers.put("User-Agent", userAgent);
        headers.put("X-Real-IP", "127.0.0.1");
        if (method == HttpMethod.POST) {
            headers.put("Content-Length", "0");
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpStr.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        httpStr.append("\r\n");
        HttpRequestDecoderImpl httpRequestDecoder = new HttpRequestDecoderImpl(requestConfig, applicationContext, null);
        httpRequestDecoder.doDecode(ByteBuffer.wrap(httpStr.toString().getBytes(Charset.defaultCharset())));
        return httpRequestDecoder.getRequest();
    }
}
