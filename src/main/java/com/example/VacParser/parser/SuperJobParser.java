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

@Component("superjob")
public class SuperJobParser implements VacancyParser {

    @Override
    public List<Vacancy> parse(String html) {
        Document doc = Jsoup.parse(html);
        List<Vacancy> vacancies = new ArrayList<>();
        Elements cards = doc.select(".f-test-search-result-item");

        for (Element card : cards) {
            if (card.text().isEmpty()) continue;

            Element titleElement = card.select("a").first();

            if (titleElement != null) {
                String title = titleElement.text();
                String link = "https://www.superjob.ru" + titleElement.attr("href");
                String salary = card.select("span.f-test-text-company-item-salary").text();
                String company = card.select("span.f-test-text-vacancy-item-company-name").text();
                String city = card.select("span.f-test-text-company-item-location").text();

                vacancies.add(new Vacancy(title, company, salary, city, link));
            }
        }
        return vacancies;
    }
}
