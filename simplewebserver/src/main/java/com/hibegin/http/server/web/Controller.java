package com.hibegin.http.server.web;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.ComponentFactory;
import com.hibegin.http.server.di.DefaultComponentFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Controller {

    protected HttpRequest request;
    protected HttpResponse response;

    public Controller() {

    }

    public Controller(HttpRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public static Controller buildController(Method method, HttpRequest request, HttpResponse response) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        Class<? extends Controller> type = method.getDeclaringClass().asSubclass(Controller.class);
        ServerConfig config = request == null ? null : request.getServerConfig();
        ComponentFactory factory = config == null ? new DefaultComponentFactory() : config.getComponentFactory();
        Controller controller = factory.createController(type, request, response);
        if (!type.isInstance(controller)) {
            throw new IllegalStateException("Component factory must return " + type.getName());
        }
        controller.request = request;
        controller.response = response;
        return controller;
    }
}
