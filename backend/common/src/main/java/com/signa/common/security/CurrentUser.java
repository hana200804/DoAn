package com.signa.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tham số controller nhận {@link SessionPrincipal} của request. Nếu endpoint không có {@link
 * Authenticated}, giá trị có thể là null (khách).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
