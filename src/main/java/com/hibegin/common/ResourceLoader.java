package com.hibegin.common;

import java.io.InputStream;

public class ResourceLoader {
    private static final ClassLoader CLASS_LOADER = ResourceLoader.class.getClassLoader();

    public static InputStream getResourceAsStream(String resourcePath) {
        return CLASS_LOADER.getResourceAsStream(resourcePath);
    }
}
