package com.signa.sign.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.signa.common.error.ApiException;
import com.signa.common.error.ErrorCode;
import com.signa.common.security.AuthInterceptor;
import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.common.security.SessionStore;
import com.signa.common.security.VehicleType;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

class LearningAccessTest {
  @Test
  void allLearningAdministrationEndpointsRequireAdmin() {
    var sessions = mock(SessionStore.class);
    var interceptor = new AuthInterceptor(sessions);
    var controller = new LearningController(null, null);
    when(sessions.find("driver"))
        .thenReturn(Optional.of(new SessionPrincipal("u", Role.DRIVER, VehicleType.CAR)));
    when(sessions.find("admin"))
        .thenReturn(Optional.of(new SessionPrincipal("a", Role.ADMIN, VehicleType.CAR)));
    var endpoints =
        Arrays.stream(LearningController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
            .filter(
                m ->
                    Arrays.stream(
                            AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class)
                                .value())
                        .anyMatch(p -> p.startsWith("/admin/")))
            .toList();
    assertThat(endpoints).hasSize(11);
    for (var method : endpoints) {
      for (String token : new String[] {"", "driver", "admin"}) {
        var request = new MockHttpServletRequest();
        if (!token.isEmpty()) request.addHeader("Authorization", "Bearer " + token);
        ErrorCode code = null;
        try {
          interceptor.preHandle(
              request, new MockHttpServletResponse(), new HandlerMethod(controller, method));
        } catch (ApiException e) {
          code = e.code();
        }
        assertThat(code)
            .as(method.getName() + ":" + token)
            .isEqualTo(
                token.isEmpty()
                    ? ErrorCode.UNAUTHENTICATED
                    : token.equals("driver") ? ErrorCode.FORBIDDEN : null);
      }
    }
  }
}
