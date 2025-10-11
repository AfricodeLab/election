package com.brandedhustlers.electionbackend.common.config.messaging;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    @Bean
    public OpenAPI customOpenAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("Blockchain Backed Election System")
                        .version("1.0")
                        .description("API Documentation for Blockchain Backed Election System")
                );
    }
}