package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MimeTypeUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(MimeTypeUtil.class);
    private static Map<String, String> map = new HashMap<String, String>();


    static {
        Properties prop = new Properties();
        try {
            prop.load(MimeTypeUtil.class.getResourceAsStream("/conf/mimetype.properties"));
            for (Entry<Object, Object> p : prop.entrySet()) {
                map.put(p.getKey().toString(), p.getValue().toString());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }


    public static String getMimeStrByExt(String ext) {
        return map.get(ext);
    }
}
