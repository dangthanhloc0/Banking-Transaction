package com.ldang03.common.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

public class JwtPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        if (!request.getHeaders().containsKey(HttpHeaderUtil.AUTHORIZATION_HEADER)) {
            String token = currentAuthorizationHeader();
            if (token != null) {
                request.getHeaders().add(HttpHeaderUtil.AUTHORIZATION_HEADER, token);
            }
        }

        return execution.execute(request, body);
    }

    private String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }

        HttpServletRequest servletRequest = attrs.getRequest();
        return servletRequest.getHeader(HttpHeaderUtil.AUTHORIZATION_HEADER);
    }
}
