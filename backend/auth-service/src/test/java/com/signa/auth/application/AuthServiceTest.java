package com.signa.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signa.auth.domain.User;
import com.signa.auth.domain.UserRepository;
import com.signa.auth.otp.OtpSender;
import com.signa.auth.otp.OtpStore;
import com.signa.common.error.ApiException;
import com.signa.common.error.ErrorCode;
import com.signa.common.ratelimit.RateLimiter;
import com.signa.common.security.Role;
import com.signa.common.security.SessionStore;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  @Test
  void concurrentDuplicateRegistrationStillReturnsConflict() {
    when(users.saveAndFlush(any()))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate email"));
    assertThatThrownBy(() -> service.register("a@example.com", "password123", "A"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
  }

  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private UserRepository users;
  private SessionStore sessions;
  private RateLimiter rateLimiter;
  private OtpStore otpStore;
  private OtpSender otpSender;
  private AuthService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    sessions = mock(SessionStore.class);
    rateLimiter = mock(RateLimiter.class);
    otpStore = mock(OtpStore.class);
    otpSender = mock(OtpSender.class);
    when(sessions.ttl()).thenReturn(Duration.ofDays(30));
    service = new AuthService(users, encoder, sessions, rateLimiter, otpStore, otpSender);
  }

  @Test
  void registerNormalizesEmailAndRejectsDuplicates() {
    when(users.existsByEmail("a@example.com")).thenReturn(false, true);

    var view = service.register("  A@Example.com ", "password123", "Tài xế A");

    assertThat(view.email()).isEqualTo("a@example.com");
    assertThat(view.role()).isEqualTo(Role.DRIVER);
    assertThatThrownBy(() -> service.register("a@example.com", "password123", "B"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  void loginWithWrongPasswordIsUnauthenticated() {
    User user = User.create("a@example.com", encoder.encode("password123"), "A", Role.DRIVER);
    when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.login("a@example.com", "wrong-password", "10.0.0.1"))
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.UNAUTHENTICATED);
    verify(sessions, never()).create(any());
  }

  @Test
  void loginCreatesSessionAndResetsRateLimit() {
    User user = User.create("a@example.com", encoder.encode("password123"), "A", Role.ADMIN);
    when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
    when(sessions.create(any())).thenReturn("session-1");

    var res = service.login("A@example.com", "password123", "10.0.0.1");

    assertThat(res.sessionId()).isEqualTo("session-1");
    assertThat(res.user().role()).isEqualTo(Role.ADMIN);
    verify(rateLimiter).check(eq("login:a@example.com:10.0.0.1"), anyInt(), any());
    verify(rateLimiter).reset("login:a@example.com:10.0.0.1");
  }

  @Test
  void changePasswordRequiresOldPasswordAndRevokesOtherSessions() {
    User user = User.create("a@example.com", encoder.encode("password123"), "A", Role.DRIVER);
    when(users.findById(user.getId())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.changePassword(user.getId(), "s1", "bad", "newpassword1"))
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);

    service.changePassword(user.getId(), "s1", "password123", "newpassword1");

    assertThat(encoder.matches("newpassword1", user.getPasswordHash())).isTrue();
    verify(sessions).deleteAllForUser(user.getId(), "s1");
  }

  @Test
  void forgotPasswordDoesNotRevealUnknownEmail() {
    when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    service.forgotPassword("ghost@example.com");

    verify(otpSender, never()).send(anyString(), anyString());
  }

  @Test
  void resetPasswordWithValidOtpRevokesAllSessions() {
    User user = User.create("a@example.com", encoder.encode("password123"), "A", Role.DRIVER);
    when(otpStore.verify("a@example.com", "123456")).thenReturn(true);
    when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));

    service.resetPassword("a@example.com", "123456", "brandnewpass");

    assertThat(encoder.matches("brandnewpass", user.getPasswordHash())).isTrue();
    verify(sessions).deleteAllForUser(user.getId(), null);
  }

  @Test
  void resetPasswordWithWrongOtpFails() {
    when(otpStore.verify("a@example.com", "000000")).thenReturn(false);

    assertThatThrownBy(() -> service.resetPassword("a@example.com", "000000", "brandnewpass"))
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void unicodePasswordsCannotBeSilentlyTruncatedByBcrypt() {
    String oversized = "ắ".repeat(24) + "A";
    assertThatThrownBy(() -> service.register("a@example.com", oversized, "A"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.resetPassword("a@example.com", "123456", oversized))
        .isInstanceOf(ApiException.class);
    verify(otpStore, never()).verify(anyString(), anyString());
    verify(users, never()).save(any());
    User legacy = User.create("a@example.com", encoder.encode(oversized), "A", Role.DRIVER);
    when(users.findByEmail("a@example.com")).thenReturn(Optional.of(legacy));
    assertThatThrownBy(() -> service.login("a@example.com", "ắ".repeat(24) + "B", "127.0.0.1"))
        .isInstanceOf(ApiException.class);
    verify(sessions, never()).create(any());
  }
}
