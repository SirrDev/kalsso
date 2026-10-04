package com.footbooking.search;

import com.footbooking.field.FieldFormat;
import com.footbooking.field.FieldSurface;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SearchService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final SearchRepository repo;

    public SearchService(SearchRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldSummary> search(String city, FieldFormat format, FieldSurface surface,
                                             int page, int size) {
        Page<FieldSummary> result = repo.search(
                normalizeCity(city), format, surface, PageRequest.of(clampPage(page), clampSize(size)));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    static String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return city.trim().toLowerCase(Locale.ROOT);
    }

    static int clampPage(int page) {
        return Math.max(page, 0);
    }

    static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}