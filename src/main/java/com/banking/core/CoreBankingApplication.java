package com.banking.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Application entry point.
 *
 * <p>{@code @EnableJpaAuditing} activates Spring Data JPA's auditing
 * infrastructure so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations on our entities are populated automatically.
 */
@SpringBootApplication
@EnableJpaAuditing
public class CoreBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingApplication.class, args);
    }
}
