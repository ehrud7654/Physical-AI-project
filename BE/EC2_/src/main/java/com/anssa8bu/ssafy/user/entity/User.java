package com.anssa8bu.ssafy.user.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private UserStatus status;

  protected User() {}

  public User(UUID id, String email, String passwordHash, String name, UserStatus status) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.email = requireText(email, "email");
    this.passwordHash = requireText(passwordHash, "passwordHash");
    this.name = requireText(name, "name");
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public void rename(String name) {
    this.name = requireText(name, "name");
  }

  public void changeStatus(UserStatus status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getName() {
    return name;
  }

  public UserStatus getStatus() {
    return status;
  }
}
