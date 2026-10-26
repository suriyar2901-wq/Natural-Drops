package com.naturaldrops;

import com.naturaldrops.config.CashfreeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CashfreeProperties.class)
public class NaturalDropsApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NaturalDropsApplication.class, args);
    }
}

