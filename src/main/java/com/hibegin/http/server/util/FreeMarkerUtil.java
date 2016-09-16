package com.hibegin.http.server.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.session.HttpSession;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class FreeMarkerUtil {

    private static Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);

    public static String renderToFM(String name, HttpRequest httpRequest) {
        try {
            Template temp = cfg.getTemplate(name + ".ftl");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(out);
            HttpSession httpSession = httpRequest.getSession();
            if (httpSession != null) {
                httpRequest.getAttr().put("session", httpSession);
                httpRequest.getAttr().put("request", httpRequest);
            }
            temp.process(httpRequest.getAttr(), writer);
            writer.flush();
            writer.close();
            return new String(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return StringsUtil.getHtmlStrByStatusCode(404);
    }

    public static void init(String basePath) throws Exception {
        cfg.setDirectoryForTemplateLoading(new File(basePath));
    }

    public static void initClassTemplate(String basePath) {
        cfg.setClassForTemplateLoading(FreeMarkerUtil.class, basePath);
    }
}
