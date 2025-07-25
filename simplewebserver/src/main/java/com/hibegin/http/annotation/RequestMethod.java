package com.hibegin.http.annotation;

import com.hibegin.http.HttpMethod;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD})
public @interface RequestMethod {

    HttpMethod method() default HttpMethod.GET;
}
