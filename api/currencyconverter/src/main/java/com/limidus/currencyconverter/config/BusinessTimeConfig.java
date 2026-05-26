package com.limidus.currencyconverter.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock businessClock(TreasuryProperties treasuryProperties) {
        ZoneId zone = ZoneId.of(treasuryProperties.getTimezone());
        return Clock.system(zone);
    }
}

