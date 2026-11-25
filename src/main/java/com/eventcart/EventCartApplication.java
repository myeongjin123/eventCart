package com.eventcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class EventCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventCartApplication.class, args);
    }
}
