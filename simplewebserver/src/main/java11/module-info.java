module hibegin.simplewebserver {
    requires java.logging;
    //requires gson;
    //requires freemarker;
    //requires gson;
    exports com.hibegin.common.util;
    exports com.hibegin.common;
    exports com.hibegin.template;
    exports com.hibegin.http;
    exports com.hibegin.http.annotation;
    exports com.hibegin.http.io;
    exports com.hibegin.http.server;
    exports com.hibegin.http.server.api;
    exports com.hibegin.http.server.config;
    exports com.hibegin.http.server.execption;
    exports com.hibegin.http.server.handler;
    exports com.hibegin.http.server.impl;
    exports com.hibegin.http.server.util;
    exports com.hibegin.http.server.web;
    exports com.hibegin.http.server.web.cookie;
    exports com.hibegin.http.server.web.session;
    exports com.hibegin.common.io.handler;
    opens template.sf;
}