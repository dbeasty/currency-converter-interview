package com.limidus.currencyconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.limidus.currencyconverter.config.TreasuryProperties;

@SpringBootApplication
@EnableConfigurationProperties(TreasuryProperties.class)
@EnableScheduling
public class CurrencyconverterApplication {

	public static void main(String[] args) {
		SpringApplication.run(CurrencyconverterApplication.class, args);
	}

}
