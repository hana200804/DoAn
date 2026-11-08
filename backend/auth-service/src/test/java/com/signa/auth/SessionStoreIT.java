package com.signa.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.common.security.SessionStore;
import com.signa.common.security.VehicleType;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Phiên trên Redis thật: cập nhật hồ sơ không được tạo lại phiên vừa đăng xuất. */
@Tag("integration")
class SessionStoreIT {

  // Giống cấu hình Jackson của Spring Boot: bỏ qua thuộc tính lạ (ví dụ "admin" từ isAdmin())
  static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static LettuceConnectionFactory factory;
  static volatile boolean logoutDuringRefresh;

  @BeforeAll
  static void setUp() {
    factory = RedisTestServer.connectionFactory();
  }

  @AfterAll
  static void tearDown() {
    factory.destroy();
  }

  @Test
  void logoutBetweenReadingTtlAndWritingIsNotUndone() {
    // Đăng xuất chen vào đúng giữa lúc refresh đã đọc TTL và trước khi ghi lại phiên
    var template =
        new StringRedisTemplate(factory) {
          @Override
          public Long getExpire(String key) {
            Long ttl = super.getExpire(key);
            if (logoutDuringRefresh) {
              delete(key);
            }
            return ttl;
          }
        };
    var sessions = new SessionStore(template, MAPPER, Duration.ofDays(30));
    String id = sessions.create(new SessionPrincipal("u1", Role.DRIVER, VehicleType.CAR));

    logoutDuringRefresh = true;
    sessions.refreshAllForUser(new SessionPrincipal("u1", Role.DRIVER, VehicleType.TRUCK));
    logoutDuringRefresh = false;

    assertThat(sessions.find(id)).isEmpty();
  }

  @Test
  void refreshUpdatesLiveSessionAndKeepsItsTtl() {
    var template = new StringRedisTemplate(factory);
    var sessions = new SessionStore(template, MAPPER, Duration.ofDays(30));
    String id = sessions.create(new SessionPrincipal("u2", Role.DRIVER, VehicleType.CAR));

    sessions.refreshAllForUser(new SessionPrincipal("u2", Role.DRIVER, VehicleType.BUS));

    assertThat(sessions.find(id))
        .get()
        .extracting(SessionPrincipal::vehicleType)
        .isEqualTo(VehicleType.BUS);
    assertThat(template.getExpire("session:" + id)).isPositive();
  }
}
