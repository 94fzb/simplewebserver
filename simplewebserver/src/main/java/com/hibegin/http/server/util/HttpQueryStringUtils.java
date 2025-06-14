package com.hibegin.http.server.util;

import java.util.*;

public class HttpQueryStringUtils {

    public static Map<String, String[]> parseUrlEncodedStrToMap(String queryString) {
        if (Objects.isNull(queryString) || queryString.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, List<String>> tempParam = new HashMap<>();
        String[] args = queryString.split("&");
        for (String string : args) {
            int idx = string.indexOf("=");
            if (idx != -1) {
                String key = string.substring(0, idx);
                String value = string.substring(idx + 1);
                if (tempParam.containsKey(key)) {
                    tempParam.get(key).add(value);
                } else {
                    List<String> paramValues = new ArrayList<>();
                    paramValues.add(value);
                    tempParam.put(key, paramValues);
                }
            }
        }
        Map<String, String[]> paramMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : tempParam.entrySet()) {
            paramMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        return paramMap;
    }
}
