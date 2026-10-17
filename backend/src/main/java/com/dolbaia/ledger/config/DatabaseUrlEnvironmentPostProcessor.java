package com.dolbaia.ledger.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Maps {@code DATABASE_URL} onto {@code spring.datasource.*} unless the
 * datasource is already configured explicitly.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String value = environment.getProperty("DATABASE_URL");
        if (value == null || value.isBlank() || environment.containsProperty("spring.datasource.url")) {
            return;
        }

        DatabaseUrl url = DatabaseUrl.parse(value);
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", url.jdbcUrl());
        if (url.username() != null) properties.put("spring.datasource.username", url.username());
        if (url.password() != null) properties.put("spring.datasource.password", url.password());
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, properties));
    }
}
