package com.hibegin.http.server.web;

import com.hibegin.common.util.LoggerUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Router {

    private final Map<String, Method> routerMap = new HashMap<String, Method>();

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
        try {
            //for graalvm
            Class.forName(clazz.getName()).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LoggerUtil.getLogger(Router.class).warning(clazz.getSimpleName() + " not find default " + "constructor");
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
        try {
            //for graalvm
            Class.forName(clazz.getName()).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LoggerUtil.getLogger(Router.class).warning(clazz.getSimpleName() + " not find default " + "constructor");
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
