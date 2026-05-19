package com.brandedhustlers.electionbackend.common.config.messaging;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Default RestTemplate uses SimpleClientHttpRequestFactory with no timeouts —
        // a slow downstream service (e.g. blockchain anchor) blocks the Tomcat thread
        // indefinitely. Explicit timeouts make failures fast instead of silent hangs.
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}