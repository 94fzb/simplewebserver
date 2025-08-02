package com.hibegin.http.server.config;

public interface HttpJsonMessageConverter {

    String toJson(Object obj) throws Exception;

    Object fromJson(String jsonStr) throws Exception;

    <T> T fromJson(String jsonStr, Class<T> clz) throws Exception;
}
