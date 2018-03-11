package com.hibegin.http.server.config;

import java.io.InputStream;

public interface StaticResourceLoader {

    InputStream getInputStream(String path);
}
