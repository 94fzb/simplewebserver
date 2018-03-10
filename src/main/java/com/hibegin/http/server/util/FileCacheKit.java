package com.hibegin.http.server.util;

import java.io.File;
import java.io.IOException;

public class FileCacheKit {

    public static File generatorRequestTempFile(int flag) throws IOException {
        return File.createTempFile("cache", suffix(flag), new File(PathUtil.getTempPath()));
    }

    private static String suffix(int flag) {
        return ".tmp." + flag;
    }

    public static void cleanByFlag(int flag) {
        File[] files = new File(PathUtil.getTempPath()).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(suffix(flag))) {
                    file.delete();
                }
            }
        }
    }
}
