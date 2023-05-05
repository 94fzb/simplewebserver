package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;

/**
 * 提供给一些路径供程序更方便的调用
 */
public class PathUtil {

    private static String ROOT_PATH = "";

    public static String getConfPath() {
        return getRootPath() + "/conf/";
    }

    public static String getRootPath() {
        if (ROOT_PATH != null && ROOT_PATH.length() > 0) {
            return ROOT_PATH;
        }
        URL url = PathUtil.class.getResource("/");
        if (Objects.isNull(url)) {
            return System.getProperty("user.dir");
        }
        String tPath = url.getPath().replace("file:", "");
        return new File(URLDecoder.decode(tPath, StandardCharsets.UTF_8)).getParent();
    }

    public static void setRootPath(String rootPath) {
        ROOT_PATH = rootPath;
    }

    public static File getConfFile(String file) {
        File nFile = new File(getConfPath() + file);
        if (nFile.exists()) {
            return nFile;
        } else {
            InputStream in = PathUtil.class.getResourceAsStream("/conf/" + file);
            if (in != null) {
                try {
                    File tempFile = File.createTempFile(nFile.getName(), ".tmp");
                    IOUtil.writeBytesToFile(IOUtil.getByteByInputStream(in), tempFile);
                    return tempFile;
                } catch (IOException e) {
                    LoggerUtil.getLogger(PathUtil.class).log(Level.SEVERE, "", e);
                }
            }
        }
        return null;
    }

    public static String getStaticPath() {
        return getRootPath() + "/static/";
    }

    public static String getTempPath() {
        String str = getRootPath() + "/temp/";
        new File(str).mkdirs();
        return str;
    }
}