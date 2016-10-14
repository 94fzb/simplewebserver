package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.util.PathUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigKit {

    private static final Logger LOGGER = LoggerUtil.getLogger(ConfigKit.class);
    private static Properties prop;

    static {
        prop = new Properties();
        try {
            File file = new File(PathUtil.getConfFile("/conf.properties"));
            if (file.exists()) {
                prop.load(new FileInputStream(file));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    public static Integer getMaxUploadSize() {
        Object maxUploadSize = prop.get("server.maxUploadSize");
        if (maxUploadSize != null) {
            return Integer.parseInt(maxUploadSize.toString());
        }
        return 20971520;
    }

    public static Integer getServerPort() {
        Object port = prop.get("server.port");
        if (port != null) {
            return Integer.parseInt(port.toString());
        }
        return 6058;
    }

    public static Integer getHttpsServerPort() {
        Object port = prop.get("server.ssl.port");
        if (port != null) {
            return Integer.parseInt(port.toString());
        }
        return 6443;
    }

    public static boolean contains(String key) {
        return prop.get(key) != null;
    }

    public static Object get(String key, Object defaultValue) {
        Object obj = prop.get(key);
        if (obj != null) {
            return obj;
        }
        return defaultValue;
    }
}
