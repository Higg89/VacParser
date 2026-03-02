package com.example.VacParser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class VacParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacParserApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(
                    HttpHeaders.USER_AGENT,
                    "VacParser/1.0 (https://example.com; contact: vacparser@example.com)"
            );
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
