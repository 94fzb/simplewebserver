package com.hibegin.template;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.FreeMarkerUtil;

import java.io.InputStream;

public class FreemarkerTemplateRender implements TemplateRender {

    private final HttpRequest request;

    public FreemarkerTemplateRender(HttpRequest request) {
        this.request = request;
    }


    @Override
    public String render(InputStream in) {
        return "";
    }

    @Override
    public String render(String templateStr) {
        return "";
    }

    @Override
    public String renderByTemplateName(String templateName) throws Exception {
        return FreeMarkerUtil.renderToFM(templateName, request);
    }
}
