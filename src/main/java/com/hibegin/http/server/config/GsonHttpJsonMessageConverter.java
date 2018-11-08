package com.hibegin.http.server.config;

import com.hibegin.common.util.LoggerUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GsonHttpJsonMessageConverter implements HttpJsonMessageConverter {

    private static final Logger LOGGER = LoggerUtil.getLogger(GsonHttpJsonMessageConverter.class);

    private Method toJson;
    private Object gson;
    private Method formJson;

    public GsonHttpJsonMessageConverter() {
        try {
            gson = Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance();
            formJson = Class.forName("com.google.gson.Gson").getMethod("fromJson", String.class, Type.class);
            toJson = Class.forName("com.google.gson.Gson").getMethod("toJson", Object.class);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public String toJson(Object obj) throws Exception {
        return (String) toJson.invoke(gson, obj);
    }

    @Override
    public Object fromJson(String jsonStr) throws Exception {
        return formJson.invoke(gson, jsonStr, Object.class);
    }
}
