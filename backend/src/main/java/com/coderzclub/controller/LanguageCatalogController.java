package com.coderzclub.controller;

import com.coderzclub.service.LanguageCapabilityCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/languages")
public class LanguageCatalogController {
    private final LanguageCapabilityCatalog catalog;

    public LanguageCatalogController(LanguageCapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of(
            "liveProviderVerified", false,
            "languages", catalog.all()
        );
    }
}
