package com.dechouriog.ledger.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String webhookSecret, String jwtSecret, boolean devTokenEnabled) {
}
