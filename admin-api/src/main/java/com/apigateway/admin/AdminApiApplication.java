package com.apigateway.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.apigateway.admin", "com.apigateway.common"})
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = {"com.apigateway.admin.entity", "com.apigateway.common.entity"})
@EnableJpaRepositories(basePackages = {"com.apigateway.admin.repository", "com.apigateway.common"})
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}
