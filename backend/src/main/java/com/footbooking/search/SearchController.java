package com.footbooking.search;

import com.footbooking.field.FieldFormat;
import com.footbooking.field.FieldSurface;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fields")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    /** Public : /api/fields/** en GET est deja ouvert dans SecurityConfig. */
    @GetMapping("/search")
    public PageResponse<FieldSummary> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) FieldFormat format,
            @RequestParam(required = false) FieldSurface surface,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(city, format, surface, page, size);
    }
}