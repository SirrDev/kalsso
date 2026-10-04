package com.footbooking.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    @Test
    void normalizeCity_trimsAndLowercases() {
        assertEquals("dakar", SearchService.normalizeCity("  Dakar "));
    }

    @Test
    void normalizeCity_blankOrNull_meansNoFilter() {
        assertNull(SearchService.normalizeCity(null));
        assertNull(SearchService.normalizeCity("   "));
    }

    @Test
    void clampSize_appliesDefaultAndMaximum() {
        assertEquals(SearchService.DEFAULT_SIZE, SearchService.clampSize(0));
        assertEquals(SearchService.DEFAULT_SIZE, SearchService.clampSize(-5));
        assertEquals(10, SearchService.clampSize(10));
        assertEquals(SearchService.MAX_SIZE, SearchService.clampSize(10_000));
    }

    @Test
    void clampPage_neverNegative() {
        assertEquals(0, SearchService.clampPage(-3));
        assertEquals(2, SearchService.clampPage(2));
    }
}