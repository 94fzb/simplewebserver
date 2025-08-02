package com.hibegin.http.server.config;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GsonHttpJsonMessageConverter implements HttpJsonMessageConverter {

    private static final Logger LOGGER = LoggerUtil.getLogger(GsonHttpJsonMessageConverter.class);

    private Method toJson;
    private Object gson;
    private Method formJson;

    public GsonHttpJsonMessageConverter() {
        if (!imported()) {
            return;
        }
        try {
            gson = Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance();
            formJson = Class.forName("com.google.gson.Gson").getMethod("fromJson", String.class, Type.class);
            toJson = Class.forName("com.google.gson.Gson").getMethod("toJson", Object.class);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    public static boolean imported() {
        try {
            Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toJson(Object obj) throws Exception {
        return (String) toJson.invoke(gson, obj);
    }

    @Override
    public Object fromJson(String jsonStr) throws Exception {
        return fromJson(jsonStr, Object.class);
    }

    @Override
    public <T> T fromJson(String jsonStr, Class<T> clz) throws Exception {
        return (T) formJson.invoke(gson, jsonStr, clz);
    }
}
