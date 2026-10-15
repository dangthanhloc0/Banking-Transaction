package com.ldang03.common.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
public class EmailTemplateBuilder {

    @Autowired
    private TemplateEngine templateEngine;

    public String build(String templateName, Map<String, Object> data) {
        Context context = new Context();

        if (data != null) {
            data.forEach(context::setVariable);
        }

        return templateEngine.process(templateName, context);
    }
}
