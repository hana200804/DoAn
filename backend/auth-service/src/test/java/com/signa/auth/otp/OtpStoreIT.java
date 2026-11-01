package com.signa.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.signa.auth.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** OTP trên Redis thật: dùng một lần và khoá sau số lần sai cho phép. */
@Tag("integration")
@Testcontainers
class OtpStoreIT {

  @Container
  static GenericContainer<?> redis = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

  static LettuceConnectionFactory factory;
  static OtpStore store;

  @BeforeAll
  static void setUp() {
    factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    factory.afterPropertiesSet();
    var template = new StringRedisTemplate(factory);
    var props = new AuthProperties(new AuthProperties.Otp(Duration.ofMinutes(5), 3), null);
    store = new OtpStore(template, props);
  }

  @AfterAll
  static void tearDown() {
    factory.destroy();
  }

  @Test
  void correctCodeWorksOnlyOnce() {
    String otp = store.issue("a@signa.vn");

    assertThat(store.verify("a@signa.vn", otp)).isTrue();
    assertThat(store.verify("a@signa.vn", otp)).isFalse();
  }

  @Test
  void tooManyWrongAttemptsBurnTheCode() {
    String otp = store.issue("b@signa.vn");
    String wrong = otp.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 3; i++) {
      assertThat(store.verify("b@signa.vn", wrong)).isFalse();
    }
    assertThat(store.verify("b@signa.vn", otp)).isFalse();
  }
}
