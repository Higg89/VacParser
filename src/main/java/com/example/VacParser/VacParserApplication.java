package com.example.VacParser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VacParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacParserApplication.class, args);
    }

}