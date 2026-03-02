package com.example.VacParser.controller;

import com.example.VacParser.model.Vacancy;
import com.example.VacParser.service.ParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ParserController {

    private final ParserService parserService;

    @GetMapping("/")
    public String home() {
        return "VacParser is running. Use POST /parse with JSON {\"url\":\"...\"} to start parsing and GET /answer?keyword=... to see results.";
    }

    // Эндпоинт для запуска парсинга
    @PostMapping("/parse")
    public ResponseEntity<String> startParsing(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body("URL is required");
        }

        parserService.startParsing(url);
        return ResponseEntity.ok("Parsing started in background for: " + url);
    }

    // Эндпоинт получения результатов
    @GetMapping("/answer")
    public List<Vacancy> getResults(@RequestParam(required = false) String keyword) {
        return parserService.getFilteredResults(keyword);
    }
}