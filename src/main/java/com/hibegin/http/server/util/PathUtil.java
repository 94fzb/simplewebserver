package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

/**
 * 提供给一些路径供程序更方便的调用，优先读取系统变量
 * sws.conf.path，sws.static.path，sws.cache.path，sws.temp.path，sws.log.path，sws.root.path
 */
public class PathUtil {

    private static String ROOT_PATH = "";

    public static String getConfPath() {
        return Objects.requireNonNullElse(System.getProperty("sws.conf.path"), getRootPath() + "/conf/");
    }

    public static String getRootPath() {
        String rootPathByEnv = System.getProperty("sws.root.path");
        if (Objects.nonNull(rootPathByEnv)) {
            return rootPathByEnv;
        }
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
            String realFileName = file;
            if(file.startsWith("/")){
                realFileName = realFileName.substring(1);
            }
            InputStream in = PathUtil.class.getResourceAsStream("/conf/" + realFileName);
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
        return nFile;
    }

    public static InputStream getConfInputStream(String file) {
        File nFile = safeAppendFilePath(getConfPath(), file);
        if (nFile.exists()) {
            try {
                return new FileInputStream(nFile);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        String realFileName = file;
        if(file.startsWith("/")){
            realFileName = realFileName.substring(1);
        }
        return PathUtil.class.getResourceAsStream("/conf/" + realFileName);
    }

    public static File safeAppendFilePath(String basePath, String appendFilePath) {
        Path resolvedPath = new File(basePath).toPath().resolve(new File(basePath + "/" + appendFilePath).toPath()).normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid file path " + appendFilePath);
        }
        return resolvedPath.toFile();
    }

    public static String getStaticPath() {
        return Objects.requireNonNullElse(System.getProperty("sws.static.path"), getRootPath() + "/static/");
    }

    public static String getCachePath() {
        return Objects.requireNonNullElse(System.getProperty("sws.cache.path"), getRootPath() + "/cache/");
    }

    public static String getLogPath() {
        return Objects.requireNonNullElse(System.getProperty("sws.log.path"), getRootPath() + "/log/");
    }

    public static File getStaticFile(String filename) {
        return safeAppendFilePath(getStaticPath(), filename);
    }

    public static String getTempPath() {
        String str = Objects.requireNonNullElse(System.getProperty("sws.temp.path"), getRootPath() + "/temp/");
        new File(str).mkdirs();
        return str;
    }

    public static void main(String[] args) {
        File file = getStaticFile("../etc/password");
        System.out.println("file = " + file);
    }
}