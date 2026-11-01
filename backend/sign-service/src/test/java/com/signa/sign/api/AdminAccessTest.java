package com.signa.sign.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signa.common.error.ApiException;
import com.signa.common.error.ErrorCode;
import com.signa.common.security.AuthInterceptor;
import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.common.security.SessionStore;
import com.signa.common.security.VehicleType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/** Mọi endpoint quản trị chỉ cho ADMIN; tài xế nhận 403, chưa đăng nhập nhận 401. */
class AdminAccessTest {

  private final SessionStore sessions = mock(SessionStore.class);
  private final AuthInterceptor interceptor = new AuthInterceptor(sessions);
  private final AdminController controller = new AdminController(null, null, null);

  private final List<Method> endpoints =
      Arrays.stream(AdminController.class.getDeclaredMethods())
          .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
          .toList();

  @Test
  void everyAdminEndpointRejectsDriversAndAnonymous() {
    when(sessions.find("driver"))
        .thenReturn(Optional.of(new SessionPrincipal("u1", Role.DRIVER, VehicleType.CAR)));
    when(sessions.find("admin"))
        .thenReturn(Optional.of(new SessionPrincipal("u2", Role.ADMIN, VehicleType.CAR)));

    assertThat(endpoints).hasSizeGreaterThanOrEqualTo(6);
    for (Method m : endpoints) {
      assertThat(errorFor(m, "driver")).as(m.getName()).isEqualTo(ErrorCode.FORBIDDEN);
      assertThat(errorFor(m, null)).as(m.getName()).isEqualTo(ErrorCode.UNAUTHENTICATED);
      assertThat(errorFor(m, "admin")).as(m.getName()).isNull();
    }
  }

  private ErrorCode errorFor(Method method, String token) {
    var request = new MockHttpServletRequest();
    if (token != null) {
      request.addHeader("Authorization", "Bearer " + token);
    }
    try {
      interceptor.preHandle(
          request, new MockHttpServletResponse(), new HandlerMethod(controller, method));
      return null;
    } catch (ApiException e) {
      return e.code();
    }
  }
}
