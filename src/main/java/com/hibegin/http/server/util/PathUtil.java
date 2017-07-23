package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 提供给一些路径供程序更方便的调用
 *
 * @author Chun
 */
public class PathUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(PathUtil.class);

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
                String tPath = PathUtil.class.getResource("/").getPath();
                try {
                    path = URLDecoder.decode(tPath, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    //e.printStackTrace();
                    path = tPath;
                }
                path = new File(path).getParentFile().getParentFile().toString();
            } else {
                if (PathUtil.class.getProtectionDomain() != null) {
                    String tPath = PathUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath().replace("\\", "/");
                    try {
                        path = URLDecoder.decode(tPath, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        //e.printStackTrace();
                        path = tPath;
                    }
                    if ("/".equals(File.separator)) {
                        path = path.substring(0, path.lastIndexOf('/'));
                    } else {
                        path = path.substring(1, path.lastIndexOf('/'));
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
                    LOGGER.log(Level.SEVERE, "", e);
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