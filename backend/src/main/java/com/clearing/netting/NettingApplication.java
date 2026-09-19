package com.clearing.netting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NettingApplication {
    public static void main(String[] args) {
        SpringApplication.run(NettingApplication.class, args);
    }
}
