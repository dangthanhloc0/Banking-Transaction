package com.ldang03.common.redis;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.tsl}")
    private boolean tsl;

    @Bean
    public GlideClient glideClient() throws Exception {

        GlideClientConfiguration config = GlideClientConfiguration.builder()
                .address(NodeAddress.builder()
                        .host(host)
                        .port(port)
                        .build())
                // redis docker không có TLS // aws có
                .useTLS(tsl)
                .build();

        return GlideClient.createClient(config).get();
    }
}
