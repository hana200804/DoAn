package com.signa.auth.application;

import com.signa.auth.api.AuthDtos.LoginResponse;
import com.signa.auth.api.AuthDtos.UserView;
import com.signa.auth.domain.User;
import com.signa.auth.domain.UserRepository;
import com.signa.auth.domain.UserStatus;
import com.signa.auth.otp.OtpSender;
import com.signa.auth.otp.OtpStore;
import com.signa.common.error.ApiException;
import com.signa.common.ratelimit.RateLimiter;
import com.signa.common.security.Role;
import com.signa.common.security.SessionStore;
import com.signa.common.security.VehicleType;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ tài khoản và phiên đăng nhập (FR-12, FR-13, FR-20). */
@Service
public class AuthService {

  static final int LOGIN_LIMIT = 10;
  static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
  static final int FORGOT_LIMIT = 3;
  static final Duration FORGOT_WINDOW = Duration.ofHours(1);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final SessionStore sessions;
  private final RateLimiter rateLimiter;
  private final OtpStore otpStore;
  private final OtpSender otpSender;

  public AuthService(
      UserRepository users,
      PasswordEncoder encoder,
      SessionStore sessions,
      RateLimiter rateLimiter,
      OtpStore otpStore,
      OtpSender otpSender) {
    this.users = users;
    this.encoder = encoder;
    this.sessions = sessions;
    this.rateLimiter = rateLimiter;
    this.otpStore = otpStore;
    this.otpSender = otpSender;
  }

  @Transactional
  public UserView register(String email, String password, String displayName) {
    String normalized = normalize(email);
    if (users.existsByEmail(normalized)) {
      throw ApiException.conflict("Email đã được đăng ký");
    }
    User user = User.create(normalized, encoder.encode(password), displayName.trim(), Role.DRIVER);
    users.save(user);
    return UserView.of(user);
  }

  @Transactional(readOnly = true)
  public LoginResponse login(String email, String password, String clientIp) {
    String normalized = normalize(email);
    // Đếm theo cả IP: người ngoài nhập sai liên tục không khoá được chủ tài khoản
    String limitKey = "login:" + normalized + ":" + clientIp;
    rateLimiter.check(limitKey, LOGIN_LIMIT, LOGIN_WINDOW);
    User user =
        users
            .findByEmail(normalized)
            .filter(u -> encoder.matches(password, u.getPasswordHash()))
            .orElseThrow(() -> ApiException.unauthenticated("Email hoặc mật khẩu không đúng"));
    if (user.getStatus() == UserStatus.LOCKED) {
      throw ApiException.forbidden("Tài khoản đã bị khoá");
    }
    rateLimiter.reset(limitKey);
    String sessionId = sessions.create(user.toPrincipal());
    return new LoginResponse(sessionId, Instant.now().plus(sessions.ttl()), UserView.of(user));
  }

  public void logout(String sessionId) {
    if (sessionId != null) {
      sessions.delete(sessionId);
    }
  }

  @Transactional(readOnly = true)
  public UserView me(String userId) {
    return UserView.of(load(userId));
  }

  @Transactional
  public UserView updateMe(String userId, String displayName, VehicleType vehicleType) {
    User user = load(userId);
    user.updateProfile(displayName == null ? null : displayName.trim(), vehicleType);
    users.save(user);
    sessions.refreshAllForUser(user.toPrincipal());
    return UserView.of(user);
  }

  @Transactional
  public void changePassword(
      String userId, String currentSessionId, String oldPassword, String newPassword) {
    User user = load(userId);
    if (!encoder.matches(oldPassword, user.getPasswordHash())) {
      throw ApiException.validation("oldPassword", "mật khẩu cũ không đúng");
    }
    user.changePassword(encoder.encode(newPassword));
    users.save(user);
    sessions.deleteAllForUser(userId, currentSessionId);
  }

  /** Luôn trả về như nhau dù email có tồn tại hay không, để không lộ danh sách tài khoản. */
  public void forgotPassword(String email) {
    String normalized = normalize(email);
    if (!rateLimiter.tryAcquire("forgot:" + normalized, FORGOT_LIMIT, FORGOT_WINDOW)) {
      return;
    }
    users
        .findByEmail(normalized)
        .ifPresent(user -> otpSender.send(normalized, otpStore.issue(normalized)));
  }

  @Transactional
  public void resetPassword(String email, String otp, String newPassword) {
    String normalized = normalize(email);
    if (!otpStore.verify(normalized, otp)) {
      throw ApiException.validation("otp", "mã không đúng hoặc đã hết hạn");
    }
    User user =
        users
            .findByEmail(normalized)
            .orElseThrow(() -> ApiException.validation("otp", "mã không đúng hoặc đã hết hạn"));
    user.changePassword(encoder.encode(newPassword));
    users.save(user);
    sessions.deleteAllForUser(user.getId(), null);
  }

  private User load(String userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> ApiException.unauthenticated("Tài khoản không còn tồn tại"));
  }

  static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
