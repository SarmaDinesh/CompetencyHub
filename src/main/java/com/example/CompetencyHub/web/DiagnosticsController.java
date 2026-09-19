package com.example.CompetencyHub.web;

import com.example.CompetencyHub.service.CatalogDiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary endpoints for observing query behaviour. Both return the same number;
 * the interesting output is in the application log, not the response body.
 *
 * <p>Remove before this branch is considered finished — diagnostics do not belong in
 * a public API surface.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final CatalogDiagnosticsService diagnosticsService;

    public DiagnosticsController(CatalogDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/n-plus-one")
    public Map<String, Integer> naive() {
        return Map.of("totalCompetencies", diagnosticsService.countCompetenciesNaive());
    }

    @GetMapping("/fetch-join")
    public Map<String, Integer> fetched() {
        return Map.of("totalCompetencies", diagnosticsService.countCompetenciesFetched());
    }
}