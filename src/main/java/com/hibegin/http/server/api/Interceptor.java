package com.hibegin.http.server.api;

public interface Interceptor {

    /**
     * @param request
     * @param response
     * @return 为 true 可以继续调用下一个 Interceptor，反之不需要继续调用一个 Interceptor
     * @throws Exception
     */
    boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception;
}
