package com.signa.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.common.error.GlobalExceptionHandler;
import com.signa.common.ratelimit.RateLimiter;
import com.signa.common.security.AuthInterceptor;
import com.signa.common.security.CurrentSessionArgumentResolver;
import com.signa.common.security.CurrentUserArgumentResolver;
import com.signa.common.security.SessionStore;
import com.signa.common.web.RequestIdFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Cấu hình dùng chung: request id, định dạng lỗi, phiên đăng nhập, phân quyền, rate limit. */
@AutoConfiguration(after = RedisAutoConfiguration.class)
public class CommonWebAutoConfiguration {

  @Bean
  FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> bean =
        new FilterRegistrationBean<>(new RequestIdFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  @Bean
  @ConditionalOnMissingBean
  GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }

  @Bean
  @ConditionalOnMissingBean
  SessionStore sessionStore(
      StringRedisTemplate redis,
      ObjectMapper mapper,
      @Value("${signa.session.ttl:P30D}") Duration ttl) {
    return new SessionStore(redis, mapper, ttl);
  }

  @Bean
  @ConditionalOnMissingBean
  RateLimiter rateLimiter(StringRedisTemplate redis) {
    return new RateLimiter(redis);
  }

  @Bean
  WebMvcConfigurer signaWebMvcConfigurer(SessionStore sessions) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(sessions)).addPathPatterns("/api/**");
      }

      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
        resolvers.add(new CurrentSessionArgumentResolver());
      }
    };
  }
}
