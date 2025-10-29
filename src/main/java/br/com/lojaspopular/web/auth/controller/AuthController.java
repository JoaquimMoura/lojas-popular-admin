package br.com.lojaspopular.web.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.AuthService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
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
    private final AuditoriaService auditoriaService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        var tokens = authService.login(req.email(), req.senha());
        auditoriaService.registrar(AuditoriaTipo.LOGIN_SUCESSO,
                "Usuário logado: " + req.email());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        var tokens = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn()));
    }
}


