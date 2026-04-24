package com.ahy.knowledgepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowledgePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgePulseApplication.class, args);
    }

}
