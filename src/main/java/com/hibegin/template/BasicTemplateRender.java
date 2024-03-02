package com.hibegin.template;

import com.hibegin.common.util.IOUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BasicTemplateRender implements TemplateRender {

    private final Map<String, Object> map;

    public BasicTemplateRender(Map<String, Object> map) {
        this.map = map;
    }

    public String render(InputStream in) {
        if (in == null) {
            return "InputStream in null";
        }
        return render(new String(IOUtil.getByteByInputStream(in), StandardCharsets.UTF_8));
    }

    public String render(String templateStr) {
        String renderResult = templateStr;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            renderResult = renderResult.replace("${" + entry.getKey() + "}", entry.getValue().toString());
        }
        return renderResult;
    }

    @Override
    public String renderByTemplateName(String templateName) {
        return render(BasicTemplateRender.class.getResourceAsStream(templateName));
    }
}
