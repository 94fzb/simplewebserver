package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class FileCacheKit {

    public static final String SERVER_WEB_SERVER_TEMP_FILE_PREFIX = "sws-cache-";

    public static File generatorRequestTempFile(String flag, byte[] bytes) throws IOException {
        File file = File.createTempFile(SERVER_WEB_SERVER_TEMP_FILE_PREFIX, suffix(flag), new File(PathUtil.getTempPath()));
        if (Objects.nonNull(bytes) && bytes.length > 0) {
            IOUtil.writeBytesToFile(bytes, file);
        } else {
            IOUtil.writeBytesToFile(new byte[0], file);
        }
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
