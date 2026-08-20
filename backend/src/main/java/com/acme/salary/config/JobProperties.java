package com.acme.salary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared tuning for the durable background jobs (bulk raises, payroll). */
@ConfigurationProperties(prefix = "app.jobs")
public record JobProperties(
        /* Refresh the run row's counts every N items so polling clients see progress. */
        int progressUpdateEvery
) {
}
