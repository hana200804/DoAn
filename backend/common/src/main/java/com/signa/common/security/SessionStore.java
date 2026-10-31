package com.signa.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Phiên đăng nhập lưu trong Redis.
 *
 * <ul>
 *   <li>{@code session:{id}} → JSON {@link SessionPrincipal}, có TTL
 *   <li>{@code user-sessions:{userId}} → tập id phiên của người dùng, để thu hồi hàng loạt
 * </ul>
 */
public class SessionStore {

  private static final String SESSION_PREFIX = "session:";
  private static final String USER_PREFIX = "user-sessions:";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final Duration ttl;

  public SessionStore(StringRedisTemplate redis, ObjectMapper mapper, Duration ttl) {
    this.redis = redis;
    this.mapper = mapper;
    this.ttl = ttl;
  }

  public Duration ttl() {
    return ttl;
  }

  /** Tạo phiên mới, trả về session id ngẫu nhiên 256 bit. */
  public String create(SessionPrincipal principal) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis.opsForValue().set(SESSION_PREFIX + id, write(principal), ttl);
    redis.opsForSet().add(USER_PREFIX + principal.userId(), id);
    redis.expire(USER_PREFIX + principal.userId(), ttl);
    return id;
  }

  public Optional<SessionPrincipal> find(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    String json = redis.opsForValue().get(SESSION_PREFIX + sessionId);
    return Optional.ofNullable(json).map(this::read);
  }

  public void delete(String sessionId) {
    find(sessionId).ifPresent(p -> redis.opsForSet().remove(USER_PREFIX + p.userId(), sessionId));
    redis.delete(SESSION_PREFIX + sessionId);
  }

  /** Thu hồi mọi phiên của người dùng, trừ {@code keepSessionId} (có thể null). */
  public void deleteAllForUser(String userId, String keepSessionId) {
    Set<String> ids = redis.opsForSet().members(USER_PREFIX + userId);
    if (ids == null) {
      return;
    }
    for (String id : ids) {
      if (!id.equals(keepSessionId)) {
        redis.delete(SESSION_PREFIX + id);
        redis.opsForSet().remove(USER_PREFIX + userId, id);
      }
    }
  }

  /** Cập nhật thông tin (vai trò, loại xe) cho mọi phiên còn hiệu lực của người dùng. */
  public void refreshAllForUser(SessionPrincipal principal) {
    Set<String> ids = redis.opsForSet().members(USER_PREFIX + principal.userId());
    if (ids == null) {
      return;
    }
    for (String id : ids) {
      String key = SESSION_PREFIX + id;
      Long remaining = redis.getExpire(key);
      if (remaining != null && remaining > 0) {
        redis.opsForValue().set(key, write(principal), Duration.ofSeconds(remaining));
      }
    }
  }

  private String write(SessionPrincipal principal) {
    try {
      return mapper.writeValueAsString(principal);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Không ghi được phiên", e);
    }
  }

  private SessionPrincipal read(String json) {
    try {
      return mapper.readValue(json, SessionPrincipal.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Phiên bị hỏng", e);
    }
  }
}
