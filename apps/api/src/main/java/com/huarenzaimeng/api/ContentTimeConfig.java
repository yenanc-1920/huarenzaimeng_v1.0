package com.huarenzaimeng.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ContentTimeConfig {
    @Bean Clock contentClock() { return Clock.systemUTC(); }
}
