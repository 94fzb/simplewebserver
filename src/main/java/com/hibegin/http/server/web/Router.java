package com.hibegin.http.server.web;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Router {

    private Map<String, Method> routerMap = new HashMap<String, Method>();

    public void addMapper(String urlPath, Class clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PUBLIC) {
                getRouterMap().put(urlPath + "/" + method.getName(), method);
            }
        }
        try {
            getRouterMap().put(urlPath, clazz.getClass().getMethod("index"));
        } catch (NoSuchMethodException | SecurityException e) {
            //e.printStackTrace();
        }
    }

    public Method getMethod(String url) {
        return getRouterMap().get(url);
    }

    public Map<String, Method> getRouterMap() {
        return routerMap;
    }

    public void setRouterMap(Map<String, Method> routerMap) {
        this.routerMap = routerMap;
    }
}
