package com.hibegin.http.server.api;

import com.hibegin.http.server.web.cookie.Cookie;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;

public interface HttpResponse {

    void writeFile(File file);

    void renderText(String text);

    void renderHtml(String urlPath);

    void renderJson(Object obj);

    void renderCode(int errorCode);

    void addCookie(Cookie cookie);

    void renderHtmlStr(String htmlContent);

    void addHeader(String name, String value);

    void redirect(String url);

    void forward(String url);

    void renderFile(File file);

    void renderFreeMarker(String name);

    void write(InputStream inputStream);

    void write(InputStream inputStream, int code);

    void send(ByteArrayOutputStream outputStream, boolean close);

    Map<String, String> getHeader();
}
