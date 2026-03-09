package com.padle.core.padelcoreservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PadelCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PadelCoreServiceApplication.class, args);
    }

}
