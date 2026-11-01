package com.signa.auth.otp;

/** Gửi mã OTP đặt lại mật khẩu cho người dùng. */
public interface OtpSender {

  void send(String email, String otp);
}
