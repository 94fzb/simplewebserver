package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.*;

public class FileCacheKit {

    public static File generatorRequestTempFile(String flag, byte[] bytes) throws IOException {
        if (bytes == null) {
            bytes = new byte[0];
        }
        File file = File.createTempFile("cache-", suffix(flag), new File(PathUtil.getTempPath()));
        IOUtil.writeBytesToFile(bytes, file);
        return file;
    }

    private static String suffix(String flag) {
        return ".tmp." + flag;
    }

    public static void cleanByFlag(int flag) {
        File[] files = new File(PathUtil.getTempPath()).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains(suffix(flag + ""))) {
                    file.delete();
                }
            }
        }
    }

    public static boolean deleteCache(File file) {
        return file.delete();
    }
}
