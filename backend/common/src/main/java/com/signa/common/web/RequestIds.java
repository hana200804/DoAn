package com.signa.common.web;

import org.slf4j.MDC;

/** Truy cập request id của request hiện tại (đặt bởi {@link RequestIdFilter}). */
public final class RequestIds {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  private RequestIds() {}

  public static String current() {
    return MDC.get(MDC_KEY);
  }
}
