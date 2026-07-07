package br.com.lojaspopular.web.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.auth.AuthService;
import br.com.lojaspopular.web.auth.dto.LoginRequest;
import br.com.lojaspopular.web.auth.dto.RefreshRequest;
import br.com.lojaspopular.web.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    var tokens = authService.login(request.email(), request.senha());
    return ResponseEntity.ok(toResponse(tokens));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    var tokens = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(toResponse(tokens));
  }

  private TokenResponse toResponse(AuthService.Tokens tokens) {
    var user = tokens.user();
    return new TokenResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.expiresIn(),
        new TokenResponse.UserResponse(user.id(), user.email(), user.roles()));
  }
}
