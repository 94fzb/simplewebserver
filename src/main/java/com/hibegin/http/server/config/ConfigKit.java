package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.util.PathUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigKit {

    private static final Logger LOGGER = LoggerUtil.getLogger(ConfigKit.class);

    private static Properties getProp() {
        Properties prop = new Properties();
        try (InputStream inputStream = PathUtil.getConfInputStream("/conf.properties")) {
            if (Objects.nonNull(inputStream)) {
                prop.load(inputStream);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return prop;
    }

    public static Integer getMaxRequestBodySize() {
        return getInt("server.maxRequestBodySize", 20971520);
    }

    public static Integer getServerPort() {
        return getInt("server.port", 6058);
    }

    public static Integer getHttpsServerPort() {
        return getInt("server.ssl.port", 6443);
    }

    public static boolean contains(String key) {
        return getProp().get(key) != null;
    }

    public static Object get(String key, Object defaultValue) {
        Object obj = getProp().get(key);
        if (obj != null) {
            return obj;
        }
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        Object obj = getProp().get(key);
        if (obj != null) {
            return Integer.parseInt(obj + "");
        }
        return defaultValue;
    }
}
