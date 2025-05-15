package com.erp.aierpbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Enable asynchronous method execution
public class AiErpBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiErpBackendApplication.class, args);
    }

}
