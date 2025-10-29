package br.com.lojaspopular.web.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
