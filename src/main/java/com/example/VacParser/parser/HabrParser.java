package com.example.VacParser.parser;

import com.example.VacParser.model.Vacancy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component("habr")
public class HabrParser implements VacancyParser {

    @Override
    public List<Vacancy> parse(String html) {
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".vacancy-card");
        List<Vacancy> vacancies = new ArrayList<>();
        for (Element card : cards) {
            String title = card.select(".vacancy-card__title").text();
            String company = card.select(".vacancy-card__company-title").text();
            String salary = card.select(".basic-salary").text();
            String city = card.select(".vacancy-card__meta").text();
            String link = "https://career.habr.com" + card.select(".vacancy-card__title a").attr("href");
            vacancies.add(new Vacancy(title, company, salary, city, link));
        }
        return vacancies;
    }
}
