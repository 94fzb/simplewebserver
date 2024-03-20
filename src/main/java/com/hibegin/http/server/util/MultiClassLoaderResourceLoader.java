package com.hibegin.http.server.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MultiClassLoaderResourceLoader {

    public static InputStream getResourceAsStream(String resourcePath) {
        List<ClassLoader> classLoaders = getClassLoaders();

        for (ClassLoader classLoader : classLoaders) {
            InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        return null; // Resource not found
    }

    private static List<ClassLoader> getClassLoaders() {
        List<ClassLoader> classLoaders = new ArrayList<>();

        // Add system class loader
        classLoaders.add(ClassLoader.getSystemClassLoader());

        // Add context class loader
        classLoaders.add(Thread.currentThread().getContextClassLoader());

        // Add other class loaders if needed

        return classLoaders;
    }
}