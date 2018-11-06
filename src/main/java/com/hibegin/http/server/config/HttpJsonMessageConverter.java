package com.hibegin.http.server.config;

public interface HttpJsonMessageConverter {

    String toJson(Object obj) throws Exception;

    Object fromJson(String jsonStr) throws Exception;
}
