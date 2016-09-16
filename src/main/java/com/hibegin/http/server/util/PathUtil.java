package com.hibegin.http.server.util;

import java.io.File;

/**
 * 提供给一些路径供程序更方便的调用
 *
 * @author Chun
 */
public class PathUtil {

    private static String ROOT_PATH = "";

    public static String getConfPath() {
        return getRootPath() + "/conf/";
    }

    public static String getRootPath() {
        if (ROOT_PATH != null && ROOT_PATH.length() > 0) {
            return ROOT_PATH;
        } else {
            String path;
            if (PathUtil.class.getResource("/") != null) {
                path = new File(PathUtil.class.getClass().getResource("/").getPath()).getParentFile().getParentFile().toString();
            } else {
                if (PathUtil.class.getProtectionDomain() != null) {
                    String thisPath = PathUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath().replace("\\", "/");
                    if ("/".equals(File.separator)) {
                        path = thisPath.substring(0, thisPath.lastIndexOf('/'));
                    } else {
                        path = thisPath.substring(1, thisPath.lastIndexOf('/'));
                    }
                } else {
                    path = "/";
                }
            }
            return path;
        }
    }

    public static void setRootPath(String rootPath) {
        ROOT_PATH = rootPath;
    }

    public static String getConfFile(String file) {
        return getConfPath() + file;
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