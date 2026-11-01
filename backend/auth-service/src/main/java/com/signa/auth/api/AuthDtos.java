package com.signa.auth.api;

import com.signa.auth.domain.User;
import com.signa.common.security.Role;
import com.signa.common.security.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** DTO của auth-service, khớp với docs/07-api.md §3. */
public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(max = 100) String displayName) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record LoginResponse(String sessionId, Instant expiresAt, UserView user) {}

  public record UserView(
      String id, String email, String displayName, Role role, VehicleType vehicleType) {

    public static UserView of(User u) {
      return new UserView(
          u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getVehicleType());
    }
  }

  public record UpdateMeRequest(
      @Size(min = 1, max = 100) String displayName, VehicleType vehicleType) {}

  public record ChangePasswordRequest(
      @NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record ForgotPasswordRequest(@NotBlank @Email String email) {}

  public record ResetPasswordRequest(
      @NotBlank @Email String email,
      @NotBlank @Pattern(regexp = "\\d{6}", message = "phải gồm 6 chữ số") String otp,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
