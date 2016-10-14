package com.hibegin.http.server.web;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Router {

    private Map<String, Method> routerMap = new HashMap<String, Method>();

    public void addMapper(String urlPath, Class<? extends Controller> clazz) {
        if (urlPath.equals("/")) {
            urlPath = "";
        }
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PUBLIC) {
                getRouterMap().put(urlPath + "/" + method.getName(), method);
            }
        }
        try {
            getRouterMap().put(urlPath + "/", clazz.getMethod("index"));
        } catch (NoSuchMethodException | SecurityException e) {
            //LOGGER.log(Level.SEVERE, "", e);
        }
    }

    public Method getMethod(String url) {
        return getRouterMap().get(url);
    }

    public Map<String, Method> getRouterMap() {
        return routerMap;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (routerMap.size() > 0) {
            sb.append("\r\n=========== Router Info ===========");
        }
        for (Map.Entry<String, Method> entry : routerMap.entrySet()) {
            sb.append("\r\n").append(entry.getKey()).append(" -> ").append(entry.getValue());
        }
        if (routerMap.size() > 0) {
            sb.append("\r\n===================================");
        }
        return sb.toString();
    }
}
