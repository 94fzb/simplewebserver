package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.session.HttpSession;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FreeMarkerUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(FreeMarkerUtil.class);
    private static Object cfg;

    static {
        try {
            cfg = Class.forName("freemarker.template.Configuration").getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "load freemarker error", e);
        }
    }

    public static String renderToFM(String name, HttpRequest httpRequest) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(out);
        Object template = cfg.getClass().getMethod("getTemplate", String.class).invoke(cfg, name + ".ftl");
        HttpSession httpSession = httpRequest.getSession();
        if (httpSession != null) {
            httpRequest.getAttr().put("session", httpSession);
        }
        httpRequest.getAttr().put("request", httpRequest);
        template.getClass().getMethod("process", Object.class, Writer.class).invoke(template, httpRequest.getAttr(), writer);
        writer.flush();
        return new String(out.toByteArray());
    }

    public static void init(String basePath) throws Exception {
        cfg.getClass().getMethod("setDirectoryForTemplateLoading", File.class).invoke(cfg, new File(basePath));
    }

    public static void initClassTemplate(String basePath) {
        try {
            cfg.getClass().getMethod("setClassForTemplateLoading", Class.class, String.class).invoke(cfg, FreeMarkerUtil.class, basePath);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, "init freemarker class path error", e);
        }
    }
}
