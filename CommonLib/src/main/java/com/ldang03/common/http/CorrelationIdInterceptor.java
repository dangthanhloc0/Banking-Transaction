//package com.ldang03.common.http;
//
//import org.slf4j.MDC;
//import org.springframework.http.HttpRequest;
//import org.springframework.http.client.ClientHttpRequestExecution;
//import org.springframework.http.client.ClientHttpRequestInterceptor;
//import org.springframework.http.client.ClientHttpResponse;
//
//import java.io.IOException;
//
//public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {
//
//    public static final String MDC_KEY = "correlationId";
//
//    private final String headerName;
//
//    public CorrelationIdInterceptor(String headerName) {
//        this.headerName = headerName;
//    }
//
//    @Override
//    public ClientHttpResponse intercept(
//            HttpRequest request,
//            byte[] body,
//            ClientHttpRequestExecution execution) throws IOException {
//
//        String correlationId = MDC.get(MDC_KEY);
//        if (correlationId == null || correlationId.isBlank()) {
//            correlationId = HttpHeaderUtil.newCorrelationId();
//        }
//
//        request.getHeaders().add(headerName, correlationId);
//
//        return execution.execute(request, body);
//    }
//}
