package com.ldang03.common.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RestTemplateProperties.class)
public class RestTemplateConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public RequestResponseLogger requestResponseLogger() {
        return new RequestResponseLogger();
    }

    private static final Logger log =
            LoggerFactory.getLogger(
                    RestTemplateConfig.class);

    @Bean(name = "restTemplate")
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            RestTemplateProperties properties,
            RequestResponseLogger requestResponseLogger) {

        RestTemplate restTemplate = builder
                // .requestFactory(() -> pooledClientHttpRequestFactory) — HttpClientPool tạm comment (thiếu httpclient5 ở runtime)
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .errorHandler(new ErrorHandler())
                .build();

//        restTemplate.getInterceptors().add(new CorrelationIdInterceptor(properties.getCorrelationIdHeader()));
//        restTemplate.getInterceptors().add(new JwtPropagationInterceptor());
//
//        if (properties.isLoggingEnabled()) {
//            restTemplate.getInterceptors().add(new LoggingInterceptor(requestResponseLogger));
//        }

        return restTemplate;
    }

    @Bean(name = "longRunRestTemplate")
    public RestTemplate longRunRestTemplate(
            RestTemplateBuilder builder,
            RestTemplateProperties properties) {

        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getLongRunReadTimeoutMs()))
                .errorHandler(new ErrorHandler())
                .build();
    }

    private RestTemplate getRestTemplate() {
        return applicationContext.getBean("restTemplate", RestTemplate.class);
    }

    private RestTemplate getLongRunRestTemplate() {
        return applicationContext.getBean("longRunRestTemplate", RestTemplate.class);
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
        log.info("Sending request to {}: {}", url, method);
        return getRestTemplate().exchange(url, method, requestEntity, responseType);
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        log.info("Sending request to {}: {}", url, method);
        return getRestTemplate().exchange(url, method, requestEntity, responseType);
    }

    public <T> ResponseEntity<T> exchange(URI uri, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
        log.info("Sending request to {}: {}", uri, method);
        return getRestTemplate().exchange(uri, method, requestEntity, responseType);
    }

    public <T> ResponseEntity<T> exchange(URI uri, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        log.info("Sending request to {}: {}", uri, method);
        return getRestTemplate().exchange(uri, method, requestEntity, responseType);
    }

    public <T> ResponseEntity<T> exchangeLongRun(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
        log.info("Sending long-running request to {}: {}", url, method);
        return getLongRunRestTemplate().exchange(url, method, requestEntity, responseType);
    }

    public <T> ResponseEntity<T> exchangeLongRun(String url, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        log.info("Sending long-running request to {}: {}", url, method);
        return getLongRunRestTemplate().exchange(url, method, requestEntity, responseType);
    }
}

