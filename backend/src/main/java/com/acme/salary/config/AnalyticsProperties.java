package com.acme.salary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Salary-distribution histogram bucket width, in USD. */
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(int distributionBucketUsd) {
}
