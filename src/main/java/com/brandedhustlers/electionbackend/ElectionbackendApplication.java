package com.brandedhustlers.electionbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ElectionbackendApplication {

	public static void main(String[] args) {

        System.setProperty("SPRING_DATASOURCE_URL", System.getenv("SPRING_DATASOURCE_URL"));
        System.setProperty("SPRING_DATASOURCE_USERNAME", System.getenv("SPRING_DATASOURCE_USERNAME"));
        System.setProperty("SPRING_DATASOURCE_PASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));

        System.setProperty("CLOUDINARY_NAME", System.getenv("CLOUDINARY_NAME"));
        System.setProperty("CLOUDINARY_API_SECRET", System.getenv("CLOUDINARY_API_SECRET"));
        System.setProperty("CLOUDINARY_API_KEY", System.getenv("CLOUDINARY_API_KEY"));

        System.setProperty("JWT_SECRET", System.getenv("JWT_SECRET"));
        System.setProperty("VOTE_SALT", System.getenv("VOTE_SALT"));

        System.setProperty("SPRING_MAIL_USERNAME", System.getenv("SPRING_MAIL_USERNAME"));
        System.setProperty("SPRING_MAIL_PASSWORD", System.getenv("SPRING_MAIL_PASSWORD"));

        System.setProperty("REDIS_URL", System.getenv("REDIS_URL"));

        System.setProperty("SENTRY_AUTH_TOKEN", System.getenv("SENTRY_AUTH_TOKEN"));

        System.setProperty("RABBITMQ_HOST", System.getenv("RABBITMQ_HOST"));
        System.setProperty("RABBITMQ_PORT", System.getenv("RABBITMQ_PORT"));
        System.setProperty("RABBITMQ_USERNAME", System.getenv("RABBITMQ_USERNAME"));
        System.setProperty("RABBITMQ_PASSWORD", System.getenv("RABBITMQ_PASSWORD"));

		SpringApplication.run(ElectionbackendApplication.class, args);
	}

}
