package com.signa.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Chỉ dùng khi phát triển: chưa có SMTP thì ghi OTP ra log. */
public class LoggingOtpSender implements OtpSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

  @Override
  public void send(String email, String otp) {
    log.warn("Chưa cấu hình SMTP (spring.mail.host). OTP cho {}: {}", email, otp);
  }
}
