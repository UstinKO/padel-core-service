package com.padle.core.padelcoreservice;

import com.padle.core.persistence.PersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PersistenceConfiguration.class)
public class PadelCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PadelCoreServiceApplication.class, args);
    }

}
