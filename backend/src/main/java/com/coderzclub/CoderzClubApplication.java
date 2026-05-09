package com.coderzclub;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableAsync
public class CoderzClubApplication {
    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(CoderzClubApplication.class, args);
    }

    private static void loadDotenv() {
        try {
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            Path dotenvPath = cwd.resolve(".env");
            if (!Files.exists(dotenvPath)) {
                dotenvPath = cwd.getParent() != null ? cwd.getParent().resolve(".env") : dotenvPath;
            }
            if (Files.exists(dotenvPath)) {
                Dotenv dotenv = Dotenv.configure()
                        .directory(dotenvPath.getParent().toString())
                        .filename(dotenvPath.getFileName().toString())
                        .load();

                dotenv.entries().forEach(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to load .env file: " + e.getMessage());
        }
    }
} 

