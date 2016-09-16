package com.hibegin.http.server.config;

import com.hibegin.http.server.util.PathUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigKit {

    private static Properties prop;

    static {
        prop = new Properties();
        try {
            File file = new File(PathUtil.getConfFile("/conf.properties"));
            if (file.exists()) {
                prop.load(new FileInputStream(file));
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    public static Object get(String key, Object defaultValue) {
        Object obj = prop.get(key);
        if (obj != null) {
            return obj;
        }
        return defaultValue;
    }
}
