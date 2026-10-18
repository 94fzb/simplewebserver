package com.hibegin.http.server.di;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Controller;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** Preserves the constructor conventions used before explicit bindings were introduced. */
public class DefaultComponentFactory implements ComponentFactory {

    @Override
    public Controller createController(Class<? extends Controller> type, HttpRequest request, HttpResponse response)
            throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        boolean hasDefaultConstructor = false;
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == HttpRequest.class && parameters[1] == HttpResponse.class) {
                return type.cast(constructor.newInstance(request, response));
            }
            if (parameters.length == 0) {
                hasDefaultConstructor = true;
            }
        }
        if (hasDefaultConstructor) {
            return type.getDeclaredConstructor().newInstance();
        }
        throw new NoSuchMethodException(type.getName() + " has no public default or (HttpRequest, HttpResponse) constructor");
    }

    @Override
    public Interceptor createInterceptor(Class<? extends Interceptor> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        return type.getDeclaredConstructor().newInstance();
    }
}
