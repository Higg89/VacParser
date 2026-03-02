package com.example.VacParser.service;

import com.example.VacParser.model.Vacancy;
import com.example.VacParser.parser.VacancyParser;
import com.example.VacParser.repository.VacancyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParserService {

    private final VacancyRepository vacancyRepository;
    private final Map<String, VacancyParser> parsers;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Set<String> inProgressUrls = ConcurrentHashMap.newKeySet();

    public void startParsing(String url) {
        if (!inProgressUrls.add(url)) {
            log.info("Parsing already in progress for url: {}", url);
            return;
        }
        executorService.submit(() -> {
            try {
                parseAndSave(url);
            } finally {
                inProgressUrls.remove(url);
            }
        });
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

        List<Vacancy> allVacancies = new ArrayList<>();
        try {
            if ("hh".equals(site)) {
                String query = extractQueryFromUrl(url);
                String initialUrl = String.format("https://api.hh.ru/vacancies?text=%s&area=1&page=0&per_page=20", query);
                String initialContent = restTemplate.getForObject(initialUrl, String.class);
                if (initialContent == null) return;

                JsonNode root = objectMapper.readTree(initialContent);
                int pages = root.path("pages").asInt(1);
                
                allVacancies.addAll(parser.parse(initialContent));

                for (int i = 1; i < pages; i++) {
                    String pageUrl = String.format("https://api.hh.ru/vacancies?text=%s&area=1&page=%d&per_page=20", query, i);
                    String pageContent = restTemplate.getForObject(pageUrl, String.class);
                    if (pageContent != null) {
                        allVacancies.addAll(parser.parse(pageContent));
                    }
                }
            } else if ("habr".equals(site)) {
                int page = 1;
                while (true) {
                    String pageUrl = url + "&page=" + page;
                    String content = restTemplate.getForObject(pageUrl, String.class);
                    if (content == null) break;

                    List<Vacancy> vacancies = parser.parse(content);
                    if (vacancies.isEmpty()) {
                        break;
                    }
                    allVacancies.addAll(vacancies);
                    page++;
                }
            }

            List<Vacancy> uniqueVacancies = allVacancies.stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Vacancy::getUrl, v -> v, (v1, v2) -> v1),
                            m -> new ArrayList<>(m.values())
                    ));

            try {
                vacancyRepository.saveAll(uniqueVacancies);
                log.info("{}: Saved {} vacancies", site, uniqueVacancies.size());
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate vacancies detected while saving for site {}", site, e);
            }
        } catch (Exception e) {
            log.error("Error parsing url: {}. Error: {}", url, e.getMessage());
        }
    }
    
    String getSiteFromUrl(String url) {
        if (url.contains("habr.com")) return "habr";
        if (url.contains("hh.ru")) return "hh";
        return null;
    }

    String extractQueryFromUrl(String url) {
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
        log.info("Shutting down parser executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in the specified time.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing shutdownNow");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
