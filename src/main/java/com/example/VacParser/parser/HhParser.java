package com.example.VacParser.parser;

import com.example.VacParser.model.Vacancy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component("hh")
@Slf4j
public class HhParser implements VacancyParser {

    @Override
    public List<Vacancy> parse(String json) {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.path("items");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String title = item.path("name").asText();
                    String company = item.path("employer").path("name").asText();
                    String city = item.path("area").path("name").asText();
                    String url = item.path("alternate_url").asText();
                    String salary = "";

                    if (item.has("salary") && !item.path("salary").isNull()) {
                        JsonNode s = item.path("salary");
                        String from = s.path("from").isMissingNode() || s.path("from").isNull() ? "" : s.path("from").asText();
                        String to = s.path("to").isMissingNode() || s.path("to").isNull() ? "" : s.path("to").asText();
                        String currency = s.path("currency").isMissingNode() || s.path("currency").isNull() ? "" : s.path("currency").asText();
                        salary = (from + " - " + to + " " + currency).trim();
                    }
                    vacancies.add(new Vacancy(title, company, salary, city, url));
                }
            }
        } catch (IOException e) {
            log.error("Error parsing HH.ru JSON", e);
        }
        return vacancies;
    }
}
