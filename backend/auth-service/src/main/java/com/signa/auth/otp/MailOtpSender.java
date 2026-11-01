package com.signa.auth.otp;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class MailOtpSender implements OtpSender {

  private final JavaMailSender mailSender;

  public MailOtpSender(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  public void send(String email, String otp) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Signa - Mã đặt lại mật khẩu");
    message.setText(
        "Mã đặt lại mật khẩu của bạn là: "
            + otp
            + "\n\nMã có hiệu lực trong thời gian ngắn. Nếu bạn không yêu cầu, hãy bỏ qua email"
            + " này.");
    mailSender.send(message);
  }
}
