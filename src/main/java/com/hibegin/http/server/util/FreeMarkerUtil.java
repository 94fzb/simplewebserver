package com.hibegin.http.server.util;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;

import java.util.logging.Logger;

/*import freemarker.template.Configuration;
import freemarker.template.Template;*/

public class FreeMarkerUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(FreeMarkerUtil.class);
    /*private static Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);*/

    public static String renderToFM(String name, HttpRequest httpRequest) {
        /*try {
            Template temp = cfg.getTemplate(name + ".ftl");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(out);
            HttpSession httpSession = httpRequest.getSession();
            if (httpSession != null) {
                httpRequest.getAttr().put("session", httpSession);
            }
            httpRequest.getAttr().put("request", httpRequest);
            temp.process(httpRequest.getAttr(), writer);
            writer.flush();
            writer.close();
            return new String(out.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }*/
        return StringsUtil.getHtmlStrByStatusCode(501);
    }

    public static void init(String basePath) throws Exception {
        /*cfg.setDirectoryForTemplateLoading(new File(basePath));*/
    }

    public static void initClassTemplate(String basePath) {
        /*cfg.setClassForTemplateLoading(FreeMarkerUtil.class, basePath);*/
    }
}
