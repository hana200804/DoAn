package com.signa.auth.config;

import com.signa.auth.otp.LoggingOtpSender;
import com.signa.auth.otp.MailOtpSender;
import com.signa.auth.otp.OtpSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  @ConditionalOnProperty(name = "spring.mail.host")
  OtpSender mailOtpSender(JavaMailSender mailSender) {
    return new MailOtpSender(mailSender);
  }

  /** Khi chưa cấu hình SMTP (môi trường phát triển), OTP chỉ được ghi ra log. */
  @Bean
  @ConditionalOnMissingBean(OtpSender.class)
  OtpSender loggingOtpSender() {
    return new LoggingOtpSender();
  }
}
