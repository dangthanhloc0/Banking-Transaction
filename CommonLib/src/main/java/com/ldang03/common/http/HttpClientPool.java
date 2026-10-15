package com.ldang03.common.http;

//import org.apache.hc.client5.http.config.ConnectionConfig;
//import org.apache.hc.client5.http.impl.classic.HttpClients;
//import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
//import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
//import org.apache.hc.core5.util.Timeout;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.ClientHttpRequestFactory;
//import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
//
//@Configuration
//@EnableConfigurationProperties(RestTemplateProperties.class)
//public class HttpClientPool {
//
//    @Bean
//    public ClientHttpRequestFactory pooledClientHttpRequestFactory(RestTemplateProperties properties) {
//        RestTemplateProperties.Pool pool = properties.getPool();
//
//        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
//                .setMaxConnTotal(pool.getMaxTotalConnections())
//                .setMaxConnPerRoute(pool.getMaxConnectionsPerRoute())
//                .setDefaultConnectionConfig(
//                        ConnectionConfig.custom()
//                                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeoutMs()))
//                                .build())
//                .build();
//
//        return new HttpComponentsClientHttpRequestFactory(
//                HttpClients.custom()
//                        .setConnectionManager(connectionManager)
//                        .build());
//    }
//}
