package com.hibegin.http.server.web;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Router {

    private final Map<String, Method> routerMap = new HashMap<>();

    public void addMapper(String urlPath, Class<? extends Controller> clazz) {
        if ("/".equals(urlPath)) {
            urlPath = "";
        }
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PUBLIC) {
                getRouterMap().put(urlPath + "/" + method.getName(), method);
            }
            if (Objects.equals(method.getName(), "index")) {
                getRouterMap().put(urlPath, method);
                getRouterMap().put(urlPath + "/", method);
            }
        }
    }

    public void addMapper(String urlPath, Class<? extends Controller> clazz, String methodName) {
        if ("/".equals(urlPath)) {
            urlPath = "";
        }
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (Objects.equals(method.getName(), methodName)) {
                getRouterMap().put(urlPath, method);
            }
        }
    }

    public Method getMethod(String url) {
        Method method = getRouterMap().get(url);
        if (method == null && url.contains("-")) {
            method = getRouterMap().get(url.substring(0, url.indexOf("-")));
        }
        return method;
    }

    public Map<String, Method> getRouterMap() {
        return routerMap;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!routerMap.isEmpty()) {
            sb.append("\r\n=========== Router Info ===========");
        }
        for (Map.Entry<String, Method> entry : routerMap.entrySet()) {
            sb.append("\r\n").append(entry.getKey()).append(" -> ").append(entry.getValue());
        }
        if (!routerMap.isEmpty()) {
            sb.append("\r\n===================================");
        }
        return sb.toString();
    }
}
