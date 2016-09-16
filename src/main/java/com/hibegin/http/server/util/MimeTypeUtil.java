package com.hibegin.http.server.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class MimeTypeUtil {

    private static Map<String, String> map = new HashMap<String, String>();

    static {
        Properties prop = new Properties();
        try {
            prop.load(MimeTypeUtil.class.getResourceAsStream("/conf/mimetype.properties"));
            for (Entry<Object, Object> p : prop.entrySet()) {
                map.put(p.getKey().toString(), p.getValue().toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static String getMimeStrByExt(String ext) {
        return map.get(ext);
    }
}
