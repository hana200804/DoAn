package com.signa.common.security;

import com.signa.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Đọc header {@code Authorization: Bearer <sessionId>}, gắn {@link SessionPrincipal} vào request và
 * kiểm tra {@link Authenticated}.
 */
public class AuthInterceptor implements HandlerInterceptor {

  public static final String PRINCIPAL_ATTR = AuthInterceptor.class.getName() + ".principal";
  public static final String SESSION_ATTR = AuthInterceptor.class.getName() + ".session";

  private final SessionStore sessions;

  public AuthInterceptor(SessionStore sessions) {
    this.sessions = sessions;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Optional<String> token = bearerToken(request);
    Optional<SessionPrincipal> principal = token.flatMap(sessions::find);
    principal.ifPresent(
        p -> {
          request.setAttribute(PRINCIPAL_ATTR, p);
          request.setAttribute(SESSION_ATTR, token.get());
        });

    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    Authenticated rule = findRule(method);
    if (rule == null) {
      return true;
    }
    SessionPrincipal p =
        principal.orElseThrow(
            () -> ApiException.unauthenticated("Cần đăng nhập hoặc phiên đã hết hạn"));
    if (rule.roles().length > 0 && Arrays.stream(rule.roles()).noneMatch(r -> r == p.role())) {
      throw ApiException.forbidden("Không đủ quyền thực hiện thao tác này");
    }
    return true;
  }

  private static Authenticated findRule(HandlerMethod method) {
    Authenticated onMethod =
        AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), Authenticated.class);
    if (onMethod != null) {
      return onMethod;
    }
    return AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), Authenticated.class);
  }

  static Optional<String> bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return Optional.empty();
    }
    String token = header.substring(7).trim();
    return token.isEmpty() ? Optional.empty() : Optional.of(token);
  }
}
