package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FileCacheKit {

    private static File NOT_FOUND_FILE = new File(PathUtil.getTempPath() + "/" + UUID.randomUUID().toString());

    public static File generatorRequestTempFile(int flag, byte[] bytes) throws IOException {
        if (bytes != null && bytes.length > 0) {
            File file = File.createTempFile("cache-", suffix(flag), new File(PathUtil.getTempPath()));
            IOUtil.writeBytesToFile(bytes, file);
            return file;
        }
        return NOT_FOUND_FILE;
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
