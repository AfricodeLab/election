package com.brandedhustlers.electionbackend;

import com.brandedhustlers.electionbackend.common.utility.configuration.DotenvInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class ElectionbackendApplication {

    public static void main(String[] args) {

        SpringApplication application = new SpringApplication(ElectionbackendApplication.class);
        application.addInitializers(new DotenvInitializer());

//        Dotenv dotenv = Dotenv.configure().load();
//
//        System.setProperty("SPRING_DATASOURCE_URL", dotenv.get("SPRING_DATASOURCE_URL"));
//        System.setProperty("SPRING_DATASOURCE_USERNAME", dotenv.get("SPRING_DATASOURCE_USERNAME"));
//        System.setProperty("SPRING_DATASOURCE_PASSWORD", dotenv.get("SPRING_DATASOURCE_PASSWORD"));
//
//        System.setProperty("CLOUDINARY_NAME", dotenv.get("CLOUDINARY_NAME"));
//        System.setProperty("CLOUDINARY_API_SECRET", dotenv.get("CLOUDINARY_API_SECRET"));
//        System.setProperty("CLOUDINARY_API_KEY", dotenv.get("CLOUDINARY_API_KEY"));
//
//        System.setProperty("JWT_SECRET", dotenv.get("JWT_SECRET"));
//        System.setProperty("VOTE_SALT", dotenv.get("VOTE_SALT"));
//
//        System.setProperty("SPRING_MAIL_USERNAME", dotenv.get("SPRING_MAIL_USERNAME"));
//        System.setProperty("SPRING_MAIL_PASSWORD", dotenv.get("SPRING_MAIL_PASSWORD"));
//
//        System.setProperty("REDIS_URL", dotenv.get("REDIS_URL"));
//
//        System.setProperty("SENTRY_AUTH_TOKEN", dotenv.get("SENTRY_AUTH_TOKEN"));
//
//        System.setProperty("RABBITMQ_HOST", dotenv.get("RABBITMQ_HOST"));
//        System.setProperty("RABBITMQ_PORT", dotenv.get("RABBITMQ_PORT"));
//        System.setProperty("RABBITMQ_USERNAME", dotenv.get("RABBITMQ_USERNAME"));
//        System.setProperty("RABBITMQ_PASSWORD", dotenv.get("RABBITMQ_PASSWORD"));
//
//        System.setProperty("RESEND_API_KEY", dotenv.get("RESEND_API_KEY"));



        application.run(args);
    }

}
