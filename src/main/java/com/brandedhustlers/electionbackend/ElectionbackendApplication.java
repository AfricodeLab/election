package com.brandedhustlers.electionbackend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ElectionbackendApplication {

	public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().load();

        System.setProperty("SPRING_DATASOURCE_URL", dotenv.get("SPRING_DATASOURCE_URL"));
        System.setProperty("SPRING_DATASOURCE_USERNAME", dotenv.get("SPRING_DATASOURCE_USERNAME"));
        System.setProperty("SPRING_DATASOURCE_PASSWORD", dotenv.get("SPRING_DATASOURCE_PASSWORD"));

        System.setProperty("CLOUDINARY_NAME", dotenv.get("CLOUDINARY_NAME"));
        System.setProperty("CLOUDINARY_API_SECRET", dotenv.get("CLOUDINARY_API_SECRET"));
        System.setProperty("CLOUDINARY_API_KEY", dotenv.get("CLOUDINARY_API_KEY"));
        System.setProperty("JWT_SECRET", dotenv.get("JWT_SECRET"));

        System.setProperty("SPRING_MAIL_USERNAME", dotenv.get("SPRING_MAIL_USERNAME"));
        System.setProperty("SPRING_MAIL_PASSWORD", dotenv.get("SPRING_MAIL_PASSWORD"));

        System.setProperty("REDIS_URL", dotenv.get("REDIS_URL"));

        System.setProperty("SENTRY_AUTH_TOKEN", dotenv.get("SENTRY_AUTH_TOKEN"));

        System.setProperty("RABBITMQ_HOST", dotenv.get("RABBITMQ_HOST"));
        System.setProperty("RABBITMQ_PORT", dotenv.get("RABBITMQ_PORT"));
        System.setProperty("RABBITMQ_USERNAME", dotenv.get("RABBITMQ_USERNAME"));
        System.setProperty("RABBITMQ_PASSWORD", dotenv.get("RABBITMQ_PASSWORD"));
		SpringApplication.run(ElectionbackendApplication.class, args);
	}

}
