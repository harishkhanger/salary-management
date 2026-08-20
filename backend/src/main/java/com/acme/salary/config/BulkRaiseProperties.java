package com.acme.salary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bulk-raise")
public record BulkRaiseProperties(int recentlyRaisedDays) {
}
