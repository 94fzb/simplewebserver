package com.hibegin.http.server.web;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.impl.HttpServlet;

public class Controller extends HttpServlet {

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
}
