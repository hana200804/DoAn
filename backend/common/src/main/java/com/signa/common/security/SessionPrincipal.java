package com.signa.common.security;

/** Người dùng gắn với một phiên đăng nhập. */
public record SessionPrincipal(String userId, Role role, VehicleType vehicleType) {

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }
}
