package com.signa.auth.application;

import com.signa.auth.config.AuthProperties;
import com.signa.auth.domain.User;
import com.signa.auth.domain.UserRepository;
import com.signa.common.security.Role;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Tạo tài khoản quản trị đầu tiên từ ADMIN_EMAIL / ADMIN_PASSWORD nếu chưa tồn tại. */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuthProperties.Admin admin;

  public AdminBootstrap(UserRepository users, PasswordEncoder encoder, AuthProperties properties) {
    this.users = users;
    this.encoder = encoder;
    this.admin = properties.admin();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
      return;
    }
    String email = admin.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      return;
    }
    if (admin.password().length() < 12) {
      log.warn("ADMIN_PASSWORD phải dài ít nhất 12 ký tự; bỏ qua việc tạo tài khoản quản trị");
      return;
    }
    users.save(User.create(email, encoder.encode(admin.password()), "Quản trị viên", Role.ADMIN));
    log.info("Đã tạo tài khoản quản trị {}", email);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
