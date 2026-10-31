package com.signa.auth.domain;

import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.common.security.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "vehicle_type", nullable = false)
  private VehicleType vehicleType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  public static User create(String email, String passwordHash, String displayName, Role role) {
    User u = new User();
    u.id = UUID.randomUUID().toString();
    u.email = email;
    u.passwordHash = passwordHash;
    u.displayName = displayName;
    u.role = role;
    u.vehicleType = VehicleType.CAR;
    u.status = UserStatus.ACTIVE;
    u.createdAt = Instant.now();
    u.updatedAt = u.createdAt;
    return u;
  }

  public SessionPrincipal toPrincipal() {
    return new SessionPrincipal(id, role, vehicleType);
  }

  public void changePassword(String newHash) {
    this.passwordHash = newHash;
    touch();
  }

  public void updateProfile(String displayName, VehicleType vehicleType) {
    if (displayName != null) {
      this.displayName = displayName;
    }
    if (vehicleType != null) {
      this.vehicleType = vehicleType;
    }
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Role getRole() {
    return role;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
