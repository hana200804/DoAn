package com.signa.sign;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/** Use an isolated local database when supplied; CI defaults to a disposable MySQL container. */
public abstract class SignDatabaseIT {
  private static MySQLContainer<?> mysql;

  @DynamicPropertySource
  static synchronized void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("SIGNA_TEST_DB_URL");
    if (url != null && !url.isBlank()) {
      properties.add("spring.datasource.url", () -> url);
      properties.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("SIGNA_TEST_DB_USER", "root"));
      properties.add(
          "spring.datasource.password",
          () -> System.getenv().getOrDefault("SIGNA_TEST_DB_PASSWORD", ""));
    } else {
      if (mysql == null) {
        mysql = new MySQLContainer<>("mysql:8.4");
        mysql.start();
      }
      properties.add("spring.datasource.url", mysql::getJdbcUrl);
      properties.add("spring.datasource.username", mysql::getUsername);
      properties.add("spring.datasource.password", mysql::getPassword);
    }
  }
}
