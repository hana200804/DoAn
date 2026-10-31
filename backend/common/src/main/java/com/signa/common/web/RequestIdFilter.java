package com.signa.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gắn X-Request-Id cho mọi request: lấy từ header (nginx sinh) hoặc tự tạo. */
public class RequestIdFilter extends OncePerRequestFilter {

  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(RequestIds.HEADER);
    String requestId =
        incoming != null && SAFE.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    MDC.put(RequestIds.MDC_KEY, requestId);
    response.setHeader(RequestIds.HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(RequestIds.MDC_KEY);
    }
  }
}
