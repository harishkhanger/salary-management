package com.acme.salary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * All timestamps are stamped in UTC (see DATABASE-DESIGN.md). Injecting the
 * Clock keeps time testable — tests pin it with Clock.fixed(...).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
