package com.example.VacParser.service;

import com.example.VacParser.model.Vacancy;
import com.example.VacParser.parser.VacancyParser;
import com.example.VacParser.repository.VacancyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ParserServiceTest {

    private VacancyRepository vacancyRepository;
    private Map<String, VacancyParser> parsers;
    private ObjectMapper objectMapper;
    private RestTemplate restTemplate;
    private MeterRegistry meterRegistry;
    private ParserService parserService;

    @BeforeEach
    void setUp() {
        vacancyRepository = mock(VacancyRepository.class);
        parsers = new HashMap<>();
        objectMapper = new ObjectMapper();
        restTemplate = mock(RestTemplate.class);
        meterRegistry = mock(MeterRegistry.class);
        parserService = new ParserService(vacancyRepository, parsers, objectMapper, restTemplate, meterRegistry);
    }

    @Test
    void getSiteFromUrlRecognizesHabrAndHh() {
        assertThat(parserService.getSiteFromUrl("https://career.habr.com/vacancies")).isEqualTo("habr");
        assertThat(parserService.getSiteFromUrl("https://hh.ru/search/vacancy")).isEqualTo("hh");
        assertThat(parserService.getSiteFromUrl("https://example.com")).isNull();
    }

    @Test
    void extractQueryFromUrlParsesCommonParams() {
        String urlWithQ = "https://hh.ru/search/vacancy?q=java+developer&area=1";
        String urlWithText = "https://hh.ru/search/vacancy?text=java+developer&area=1";
        String urlWithKeywords = "https://hh.ru/search/vacancy?keywords=java+developer&area=1";

        assertThat(parserService.extractQueryFromUrl(urlWithQ)).isEqualTo("java developer");
        assertThat(parserService.extractQueryFromUrl(urlWithText)).isEqualTo("java developer");
        assertThat(parserService.extractQueryFromUrl(urlWithKeywords)).isEqualTo("java developer");
    }

    @Test
    void getFilteredResultsFiltersByKeywordInTitleOrCompany() {
        Vacancy v1 = new Vacancy("Java Developer", "Acme", "100k", "Moscow", "http://test1");
        Vacancy v2 = new Vacancy("Python Developer", "Best Java Company", "120k", "Spb", "http://test2");
        Vacancy v3 = new Vacancy("Manager", "Other", "80k", "Spb", "http://test3");

        List<Vacancy> all = Arrays.asList(v1, v2, v3);

        org.mockito.Mockito.when(vacancyRepository.findAll()).thenReturn(all);

        List<Vacancy> filtered = parserService.getFilteredResults("java");

        assertThat(filtered)
                .extracting(Vacancy::getUrl)
                .containsExactlyInAnyOrder("http://test1", "http://test2");
    }
}

