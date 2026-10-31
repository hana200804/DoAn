package com.signa.common.ratelimit;

import com.signa.common.error.ApiException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Giới hạn tần suất theo cửa sổ cố định, đếm bằng Redis INCR. */
public class RateLimiter {

  private final StringRedisTemplate redis;

  public RateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** Trả true nếu còn trong giới hạn và tăng bộ đếm. */
  public boolean tryAcquire(String key, int limit, Duration window) {
    String redisKey = "ratelimit:" + key;
    Long count = redis.opsForValue().increment(redisKey);
    if (count != null && count == 1L) {
      redis.expire(redisKey, window);
    }
    return count != null && count <= limit;
  }

  /** Như {@link #tryAcquire} nhưng ném lỗi 429 khi vượt giới hạn. */
  public void check(String key, int limit, Duration window) {
    if (!tryAcquire(key, limit, window)) {
      throw ApiException.rateLimited("Thao tác quá nhiều lần, vui lòng thử lại sau");
    }
  }

  public void reset(String key) {
    redis.delete("ratelimit:" + key);
  }
}
