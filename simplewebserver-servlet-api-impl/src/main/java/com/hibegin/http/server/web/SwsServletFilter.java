package com.hibegin.http.server.web;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.handler.SwsRequestHandlerRunnable;
import com.hibegin.http.server.impl.SwsHttpServletRequestWrapper;
import com.hibegin.http.server.impl.SwsHttpServletResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;


public abstract class SwsServletFilter extends HttpFilter {

    private ApplicationContext applicationContext;
    protected AbstractServerConfig serverConfig;

    protected abstract AbstractServerConfig getServerConfig();

    @Override
    public void init() {
        System.getProperties().put("sws.conf.path", getServletContext().getRealPath("/WEB-INF/"));
        System.getProperties().put("sws.root.path", getServletContext().getRealPath("/"));
        String servletRootFile = new File(getServletContext().getRealPath("/")).getParentFile().getParent();
        System.getProperties().put("sws.log.path",  servletRootFile + "/logs");
        System.getProperties().put("sws.temp.path",  servletRootFile + "/temp");
        this.serverConfig = getServerConfig();
        applicationContext = new ApplicationContext(serverConfig.getServerConfig());
        applicationContext.init();
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        SwsHttpServletRequestWrapper requestWrapper = new SwsHttpServletRequestWrapper(req, serverConfig.getRequestConfig(), applicationContext);
        new SwsRequestHandlerRunnable(requestWrapper, new SwsHttpServletResponseWrapper(requestWrapper, serverConfig.getResponseConfig(), res), res).run();
    }
}
