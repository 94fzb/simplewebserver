package com.hibegin.template;

import java.io.InputStream;

public interface TemplateRender {

    String render(InputStream in);

    String render(String templateStr);

    String renderByTemplateName(String templateName) throws Exception;
}
