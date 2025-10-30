//package com.brandedhustlers.electionbackend.common.config.messaging;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.thymeleaf.spring6.SpringTemplateEngine;
//import org.thymeleaf.templatemode.TemplateMode;
//import org.thymeleaf.templateresolver.StringTemplateResolver;
//
//@Configuration
//public class ThymeLeafConfig {
//    @Bean
//    public SpringTemplateEngine templateEngine() {
//        SpringTemplateEngine engine = new SpringTemplateEngine();
//        engine.setTemplateResolver(stringTemplateResolver());
//        return engine;
//    }
//
//    @Bean
//    public StringTemplateResolver stringTemplateResolver() {
//        StringTemplateResolver resolver = new StringTemplateResolver();
//        resolver.setTemplateMode(TemplateMode.HTML);
//        resolver.setCacheable(false);
//        return resolver;
//    }
//}
