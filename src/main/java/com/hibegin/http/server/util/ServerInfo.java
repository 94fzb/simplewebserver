package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
        InputStream inputStream = ServerInfo.class.getResourceAsStream("/ServerInfo.properties");
        if (inputStream != null) {
            try {
                properties.load(inputStream);
                name = properties.get("server.name").toString();
                if (properties.get("server.version") != null && !"".equals(properties.get("server.version"))) {
                    version = properties.get("server.version").toString();
                }
                if (properties.get("server.buildTime") != null && !"".equals(properties.get("server.buildTime"))) {
                    time = new SimpleDateFormat("yyyy-MM-dd hh:mm").parse(properties.get("server.buildTime").toString());
                }
            } catch (IOException | ParseException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        if (name == null) {
            name = "SimpleWebServer";
        }
        if (version == null) {
            version = "0.1";
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
