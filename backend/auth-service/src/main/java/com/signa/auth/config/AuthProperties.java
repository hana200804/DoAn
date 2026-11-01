package com.signa.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signa")
public record AuthProperties(Otp otp, Admin admin) {

  public record Otp(Duration ttl, int maxAttempts) {}

  public record Admin(String email, String password) {}
}
