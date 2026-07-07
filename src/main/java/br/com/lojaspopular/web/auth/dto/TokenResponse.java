package br.com.lojaspopular.web.auth.dto;

import java.util.Set;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserResponse user) {

  public record UserResponse(Long id, String email, Set<String> roles) {
  }
}
