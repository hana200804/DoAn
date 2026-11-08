package com.signa.auth;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;

/** CI uses Redis 7.4 in Docker; local runs may use an isolated test instance. */
public final class RedisTestServer {
  private static GenericContainer<?> container;

  private RedisTestServer() {}

  public static synchronized LettuceConnectionFactory connectionFactory() {
    String host = System.getenv("SIGNA_TEST_REDIS_HOST");
    int port;
    if (host == null || host.isBlank()) {
      if (container == null) {
        container = new GenericContainer<>("redis:7.4").withExposedPorts(6379);
        container.start();
      }
      host = container.getHost();
      port = container.getMappedPort(6379);
    } else {
      port = Integer.parseInt(System.getenv().getOrDefault("SIGNA_TEST_REDIS_PORT", "6379"));
    }
    var factory = new LettuceConnectionFactory(host, port);
    factory.afterPropertiesSet();
    return factory;
  }
}
