package com.example.VacParser.service;

import com.example.VacParser.model.Vacancy;
import com.example.VacParser.repository.VacancyRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParserService {

    private final VacancyRepository vacancyRepository;
    
    // Пул потоков для асинхронного выполнения
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // Настраиваем WebClient, чтобы он максимально был похож на настоящий браузер (для обхода защиты HH.ru)
    private final WebClient webClient = WebClient.builder()
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .defaultHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    public void startParsing(String url) {
        executorService.submit(() -> parseAndSave(url));
    }

    private void parseAndSave(String url) {
        log.info("Thread [{}] started parsing: {}", Thread.currentThread().getName(), url);

        try {
            // Получаем HTML страницу
            String html = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (html != null) {
                Document doc = Jsoup.parse(html);
                
                // Выбор стратегии парсинга в зависимости от домена
                if (url.contains("habr.com")) {
                    parseHabr(doc);
                } else if (url.contains("hh.ru")) {
                    parseHh(doc);
                } else if (url.contains("superjob.ru")) {
                    parseSuperJob(doc);
                } else {
                    log.warn("Unknown domain for URL: {}", url);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing url: {}. Error: {}", url, e.getMessage());
        }
    }

    // --- ЛОГИКА ДЛЯ HABR CAREER ---
    private void parseHabr(Document doc) {
        Elements cards = doc.select(".vacancy-card");
        int count = 0;
        for (Element card : cards) {
            String title = card.select(".vacancy-card__title").text();
            String company = card.select(".vacancy-card__company-title").text();
            String salary = card.select(".basic-salary").text();
            String city = card.select(".vacancy-card__meta").text();
            String link = "https://career.habr.com" + card.select(".vacancy-card__title a").attr("href");

            saveVacancy(title, company, salary, city, link);
            count++;
        }
        log.info("Habr: Saved {} vacancies", count);
    }

    // --- ЛОГИКА ДЛЯ HH.RU ---
    private void parseHh(Document doc) {
        // HH использую data-qa атрибуты, они стабильнее обычных классов
        Elements cards = doc.select("[data-qa='vacancy-serp__vacancy']");
        int count = 0;
        
        if (cards.isEmpty()) {
            log.warn("HH.ru: No vacancies found. Possible captcha or structure change.");
        }

        for (Element card : cards) {
            String title = card.select("[data-qa='vacancy-serp__vacancy-title']").text();
            String company = card.select("[data-qa='vacancy-serp__vacancy-employer']").text();
            String salary = card.select("[data-qa='vacancy-serp__vacancy-compensation']").text();
            String city = card.select("[data-qa='vacancy-serp__vacancy-address']").text();
            String link = card.select("[data-qa='vacancy-serp__vacancy-title']").attr("href");

            saveVacancy(title, company, salary, city, link);
            count++;
        }
        log.info("HH.ru: Saved {} vacancies", count);
    }

    // --- ЛОГИКА ДЛЯ SUPERJOB ---
    private void parseSuperJob(Document doc) {
        // SuperJob часто меняет классы на рандомные (напр. _2JivQ). 
        // Надежнее искать по семантическим блокам или тестовым классам f-test-
        Elements cards = doc.select(".f-test-search-result-item");
        int count = 0;

        for (Element card : cards) {
            // Пропускаем рекламные блоки или блоки "курсов", если они попадаются
            if (card.text().isEmpty()) continue;

            // Ищем первую ссылку внутри карточки, обычно это заголовок
            Element titleElement = card.select("a").first(); 
            
            if (titleElement != null) {
                String title = titleElement.text();
                String link = "https://www.superjob.ru" + titleElement.attr("href");
                
                // Зарплата часто лежит в span с определенными классами, но они меняются.
                // Попробуем взять текст элемента, который похож на деньги
                String salary = card.select("span.f-test-text-company-item-salary").text();
                
                // Название компании
                String company = card.select("span.f-test-text-vacancy-item-company-name").text();
                
                // Город
                String city = card.select("span.f-test-text-company-item-location").text();

                saveVacancy(title, company, salary, city, link);
                count++;
            }
        }
        log.info("SuperJob: Saved {} vacancies", count);
    }

    private void saveVacancy(String title, String company, String salary, String city, String url) {
        if (salary == null || salary.isEmpty()) salary = "Не указана";
        if (company == null || company.isEmpty()) company = "Не указана";
        
        Vacancy vacancy = new Vacancy(title, company, salary, city, url);
        vacancyRepository.save(vacancy);
    }

    public List<Vacancy> getFilteredResults(String keyword) {
        List<Vacancy> all = vacancyRepository.findAll();
        if (keyword == null || keyword.isEmpty()) {
            return all;
        }
        return all.parallelStream()
                .filter(v -> v.getTitle().toLowerCase().contains(keyword.toLowerCase()) 
                          || v.getCompany().toLowerCase().contains(keyword.toLowerCase()))
                .sorted(Comparator.comparing(Vacancy::getTitle))
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 600000)
    public void scheduledLogTask() {
        log.info("Scheduled Task: Current database contains {} vacancies.", vacancyRepository.count());
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}