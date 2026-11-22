package ru.mirea.autosalon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutosalonApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutosalonApplication.class, args);
    }
}
