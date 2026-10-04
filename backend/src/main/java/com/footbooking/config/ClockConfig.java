package com.footbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Horloge injectable : les tests la remplacent par une horloge fixe. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}