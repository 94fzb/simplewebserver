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
    private static String name;
    private static String version;
    private static Date time;

    static {
        Properties properties = new Properties();
        InputStream inputStream = ServerInfo.class.getResourceAsStream("/META-INF/maven/com.hibegin/simplewebserver/pom.properties");
        if (inputStream != null) {
            try {
                properties.load(inputStream);
                if (properties.get("version") != null && !"".equals(properties.get("version"))) {
                    version = properties.get("version").toString();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        } else {
            version = "0.2-dev";
        }
        if (name == null) {
            name = "SimpleWebServer";
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
