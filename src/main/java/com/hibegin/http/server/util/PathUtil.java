package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
        if (ROOT_PATH != null && !ROOT_PATH.isEmpty()) {
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
        File nFile = safeAppendFilePath(getConfPath(), file);
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

    public static File safeAppendFilePath(String basePath, String appendFilePath) {
        Path resolvedPath = new File(basePath).toPath().resolve(new File(basePath + "/" + appendFilePath).toPath()).normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid file path " + appendFilePath);
        }
        return resolvedPath.toFile();
    }

    public static String getStaticPath() {
        return getRootPath() + "/static/";
    }

    public static File getStaticFile(String filename) {
        return safeAppendFilePath(getStaticPath(), filename);
    }

    public static String getTempPath() {
        String str = getRootPath() + "/temp/";
        new File(str).mkdirs();
        return str;
    }

    public static void main(String[] args) {
        File file = getStaticFile("../etc/password");
        System.out.println("file = " + file);
    }
}