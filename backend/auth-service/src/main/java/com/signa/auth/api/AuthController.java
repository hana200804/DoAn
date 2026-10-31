package com.signa.auth.api;

import com.signa.auth.api.AuthDtos.ForgotPasswordRequest;
import com.signa.auth.api.AuthDtos.LoginRequest;
import com.signa.auth.api.AuthDtos.LoginResponse;
import com.signa.auth.api.AuthDtos.RegisterRequest;
import com.signa.auth.api.AuthDtos.ResetPasswordRequest;
import com.signa.auth.api.AuthDtos.UserView;
import com.signa.auth.application.AuthService;
import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserView register(@Valid @RequestBody RegisterRequest req) {
    return auth.register(req.email(), req.password(), req.displayName());
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest req) {
    return auth.login(req.email(), req.password());
  }

  @PostMapping("/logout")
  @Authenticated
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@CurrentSession String sessionId) {
    auth.logout(sessionId);
  }

  @PostMapping("/password/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgot(@Valid @RequestBody ForgotPasswordRequest req) {
    auth.forgotPassword(req.email());
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Valid @RequestBody ResetPasswordRequest req) {
    auth.resetPassword(req.email(), req.otp(), req.newPassword());
  }
}
