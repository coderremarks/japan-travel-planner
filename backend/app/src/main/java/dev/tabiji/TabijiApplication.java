package dev.tabiji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Tabiji 旅路 — Japan Intelligent Travel Planner API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TabijiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TabijiApplication.class, args);
    }
}
