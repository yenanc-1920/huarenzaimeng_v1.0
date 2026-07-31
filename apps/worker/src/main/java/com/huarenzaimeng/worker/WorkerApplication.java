package com.huarenzaimeng.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WorkerApplication.class);
        application.setDefaultProperties(java.util.Map.of("spring.main.web-application-type", "none"));
        application.run(args);
    }
}
