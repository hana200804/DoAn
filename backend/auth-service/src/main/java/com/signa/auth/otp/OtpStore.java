package com.signa.auth.otp;

import com.signa.auth.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Lưu OTP dạng băm trong Redis: {@code otp:{email}} → hash {code, attempts}, có TTL. Mã đúng hoặc
 * nhập sai quá số lần cho phép thì bị xoá.
 */
@Component
public class OtpStore {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;
  private final AuthProperties.Otp config;

  public OtpStore(StringRedisTemplate redis, AuthProperties properties) {
    this.redis = redis;
    this.config = properties.otp();
  }

  /** Sinh OTP 6 chữ số mới, thay thế OTP cũ nếu có. */
  public String issue(String email) {
    String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
    String key = key(email);
    redis.delete(key);
    redis.opsForHash().putAll(key, Map.of("code", hash(email, otp), "attempts", "0"));
    redis.expire(key, config.ttl());
    return otp;
  }

  /** Kiểm tra OTP; trả true và xoá OTP nếu đúng. */
  public boolean verify(String email, String otp) {
    String key = key(email);
    Object stored = redis.opsForHash().get(key, "code");
    if (stored == null) {
      return false;
    }
    if (MessageDigest.isEqual(
        stored.toString().getBytes(StandardCharsets.UTF_8),
        hash(email, otp).getBytes(StandardCharsets.UTF_8))) {
      redis.delete(key);
      return true;
    }
    Long attempts = redis.opsForHash().increment(key, "attempts", 1);
    if (attempts != null && attempts >= config.maxAttempts()) {
      redis.delete(key);
    }
    return false;
  }

  private static String key(String email) {
    return "otp:" + email;
  }

  private static String hash(String email, String otp) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] out = digest.digest((email + ":" + otp).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(out);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
