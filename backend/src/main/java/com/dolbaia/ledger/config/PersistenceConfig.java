package com.dolbaia.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Repositories may be nested in a holder class (see FitnessRepositories). */
@Configuration
@EnableJpaRepositories(basePackages = "com.dolbaia.ledger", considerNestedRepositories = true)
public class PersistenceConfig {}
