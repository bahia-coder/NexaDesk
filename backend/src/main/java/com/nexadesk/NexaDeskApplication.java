package com.nexadesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NexaDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexaDeskApplication.class, args);
    }
}
