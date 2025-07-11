package com.hibegin.http.server.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;

/**
 * default not find resource mapping
 */
public class NotFindResourceInterceptor implements Interceptor {
    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        response.renderCode(404);
        return true;
    }
}