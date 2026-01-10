package com.example.VacParser.parser;

import com.example.VacParser.model.Vacancy;
import java.util.List;

public interface VacancyParser {
    List<Vacancy> parse(String source);
}
