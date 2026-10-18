package com.hibegin.http.server.di;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Controller;

import java.lang.reflect.InvocationTargetException;

/** Application-local construction of framework components. */
public interface ComponentFactory {

    /** Must return a fresh controller for each dispatch. SWS attaches the request and response. */
    Controller createController(Class<? extends Controller> type, HttpRequest request, HttpResponse response)
            throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException;

    /** Called during ApplicationContext initialization, in configured interceptor order. */
    Interceptor createInterceptor(Class<? extends Interceptor> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException;
}
