package com.acme.salary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(int maxPageSize) {

    /** Clamps a client-supplied page size to [1, maxPageSize]. */
    public int clampSize(int size) {
        return Math.min(Math.max(size, 1), maxPageSize);
    }

    /** Negative page indexes from clients are treated as the first page. */
    public int clampPage(int page) {
        return Math.max(page, 0);
    }
}
