package com.example.VacParser.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Vacancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String company;
    private String salary;
    private String city;
    private String url;

    private LocalDateTime parsedAt;

    public Vacancy(String title, String company, String salary, String city, String url) {
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.city = city;
        this.url = url;
        this.parsedAt = LocalDateTime.now();
    }
}