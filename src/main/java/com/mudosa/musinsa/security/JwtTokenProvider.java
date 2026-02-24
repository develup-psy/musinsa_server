package com.mudosa.musinsa.security;

import com.mudosa.musinsa.exception.CustomJwtException;
import com.mudosa.musinsa.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey secretKey;

    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        byte[] keyBytes = resolveKeyBytes(jwtSecret);
        secretKey = Keys.hmacShaKeyFor(keyBytes);
        jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    private byte[] resolveKeyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank");
        }

        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
            log.warn("JWT secret is Base64 but too short. Fallback to SHA-256 digest key.");
        } catch (RuntimeException e) {
            log.warn("JWT secret is not Base64. Fallback to SHA-256 digest key.");
        }

        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize JWT signing key", e);
        }
    }

    /* accessToken 생성 */
    public String createToken(Long userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /* refreshToken 생성 */
    public String createRefreshToken(Long userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public long getRemainingExpiration(String token) {
        Claims claims = jwtParser
                .parseSignedClaims(token).getPayload();
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            throw new CustomJwtException(ErrorCode.INVALID_JWT);
        } catch (ExpiredJwtException e) {
            throw new CustomJwtException(ErrorCode.EXPIRED_JWT);
        } catch (UnsupportedJwtException e) {
            throw new CustomJwtException(ErrorCode.UNSUPPORTED_JWT);
        } catch (IllegalArgumentException e) {
            throw new CustomJwtException(ErrorCode.EMPTY_JWT);
        }
    }

    public String getUserIdFromJWt(String token) {
        Claims claims = jwtParser
                .parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = jwtParser
                .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = jwtParser
                .parseSignedClaims(token).getPayload();
        return claims.get("role", String.class);
    }
}
