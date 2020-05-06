package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerInfo {

    private static final Logger LOGGER = LoggerUtil.getLogger(FreeMarkerUtil.class);
    private static final String name = "SimpleWebServer";
    private static String version;
    private static Date time;

    static {
        Properties properties = new Properties();
        InputStream inputStream = ServerInfo.class.getClassLoader().getResourceAsStream(name + "-git.properties");
        if (inputStream != null) {
            try {
                properties.load(inputStream);
                String key = "git.build.version";
                if (properties.get(key) != null && !"".equals(properties.get(key))) {
                    version = properties.get(key).toString();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        } else {
            version = "0.2.45";
        }
        if (time == null) {
            time = new Date();
        }

    }

    public static String getName() {
        return name;
    }

    public static String getVersion() {
        return version;
    }

    public static Date getTime() {
        return time;
    }

    public static void main(String[] args) {
        System.out.println("name = " + name);
        System.out.println("version = " + version);
        System.out.println("time = " + time);
    }
}
