package com.example.VacParser.service;

import com.example.VacParser.model.Vacancy;
import com.example.VacParser.parser.VacancyParser;
import com.example.VacParser.repository.VacancyRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParserService {

    private final VacancyRepository vacancyRepository;
    private final Map<String, VacancyParser> parsers;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
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
        String site = getSiteFromUrl(url);
        if (site == null) {
            log.warn("Unknown domain for URL: {}", url);
            return;
        }

        VacancyParser parser = parsers.get(site);
        if (parser == null) {
            log.warn("No parser found for site: {}", site);
            return;
        }

        try {
            String contentUrl = url;
            if ("hh".equals(site)) {
                String query = extractQueryFromUrl(url);
                contentUrl = String.format("https://api.hh.ru/vacancies?text=%s&area=1&page=0&per_page=20", query);
            }

            String content = webClient.get().uri(contentUrl).retrieve().bodyToMono(String.class).block();

            if (content != null) {
                List<Vacancy> vacancies = parser.parse(content);
                vacancyRepository.saveAll(vacancies);
                log.info("{}: Saved {} vacancies", site, vacancies.size());
            }
        } catch (Exception e) {
            log.error("Error parsing url: {}. Error: {}", url, e.getMessage());
        }
    }

    private String getSiteFromUrl(String url) {
        if (url.contains("habr.com")) return "habr";
        if (url.contains("hh.ru")) return "hh";
        return null;
    }

    private String extractQueryFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String[] params = uri.getQuery().split("&");
            for (String param : params) {
                String[] pair = param.split("=");
                if (pair.length == 2 && ("q".equals(pair[0]) || "text".equals(pair[0]) || "keywords".equals(pair[0]))) {
                    return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract query param from URL '{}'", url, e);
        }
        return "";
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
