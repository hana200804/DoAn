package com.signa.auth.api;

import com.signa.auth.api.AuthDtos.ChangePasswordRequest;
import com.signa.auth.api.AuthDtos.UpdateMeRequest;
import com.signa.auth.api.AuthDtos.UserView;
import com.signa.auth.application.AuthService;
import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentSession;
import com.signa.common.security.CurrentUser;
import com.signa.common.security.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Authenticated
public class UserController {

  private final AuthService auth;

  public UserController(AuthService auth) {
    this.auth = auth;
  }

  @GetMapping
  public UserView me(@CurrentUser SessionPrincipal user) {
    return auth.me(user.userId());
  }

  @PatchMapping
  public UserView update(
      @CurrentUser SessionPrincipal user, @Valid @RequestBody UpdateMeRequest req) {
    return auth.updateMe(user.userId(), req.displayName(), req.vehicleType());
  }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @CurrentUser SessionPrincipal user,
      @CurrentSession String sessionId,
      @Valid @RequestBody ChangePasswordRequest req) {
    auth.changePassword(user.userId(), sessionId, req.oldPassword(), req.newPassword());
  }
}
