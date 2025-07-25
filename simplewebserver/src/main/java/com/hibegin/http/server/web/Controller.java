package com.hibegin.http.server.web;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;

import java.lang.reflect.Constructor;
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
        Constructor<?>[] constructors = method.getDeclaringClass().getConstructors();
        boolean haveDefaultConstructor = false;
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterTypes().length == 2) {
                if (constructor.getParameterTypes()[0].getName().equals(HttpRequest.class.getName()) && constructor.getParameterTypes()[1].getName().equals(HttpResponse.class.getName())) {
                    return (Controller) constructor.newInstance(request, response);
                }
            }
            if (constructor.getParameterTypes().length == 0) {
                haveDefaultConstructor = true;
            }
        }
        if (haveDefaultConstructor) {
            Controller controller = (Controller) method.getDeclaringClass().getDeclaredConstructor().newInstance();
            controller.request = request;
            controller.response = response;
            return controller;
        }
        throw new RuntimeException(method.getDeclaringClass().getSimpleName() + " not find default " + "constructor");
    }
}
