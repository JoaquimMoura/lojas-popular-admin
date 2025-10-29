package br.com.lojaspopular.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

  private final Key key;
  private final String issuer;
  private final long accessTtl;
  private final long refreshTtl;

  public JwtUtil(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.access-token.ttl-seconds}") long accessTtl,
      @Value("${security.jwt.refresh-token.ttl-seconds}") long refreshTtl) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public String generateAccessToken(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(subject)
        .setIssuer(issuer)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(accessTtl)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateRefreshToken(String subject) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject(subject)
        .setIssuer(issuer)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(refreshTtl)))
        .claim("typ", "refresh")
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public Jws<Claims> parse(String token) {
    return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
  }
}
