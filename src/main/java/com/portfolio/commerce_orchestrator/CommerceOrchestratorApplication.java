package com.portfolio.commerce_orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CommerceOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceOrchestratorApplication.class, args);
    }

}
