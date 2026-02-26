package com.mudosa.musinsa.config;

import com.mudosa.musinsa.security.JwtAuthenticationFilter;
import com.mudosa.musinsa.security.JwtTokenProvider;
import com.mudosa.musinsa.security.RestAccessDeniedHandler;
import com.mudosa.musinsa.security.RestAuthenticationEntryPoint;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {
  private final JwtTokenProvider jwtTokenProvider;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);

    return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable)
            .requestCache(RequestCacheConfigurer::disable)

            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .securityContext(context -> context
                    .securityContextRepository(new RequestAttributeSecurityContextRepository())
                    .requireExplicitSave(true))

            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            .authorizeHttpRequests(
                    auth ->
                            auth
                                    .requestMatchers("/ws/**").permitAll()
                                    .requestMatchers("/ws-native/**").permitAll()
                                    .requestMatchers("/brand/**").permitAll()
                                    .requestMatchers("/chat/**").permitAll()
                                    .requestMatchers("/api-docs/**").permitAll()
                                    // 인증 없이 접근 가능한 경로
                                    .requestMatchers(
                                            "/api/auth/**",           // 로그인, 회원가입, 토큰 갱신
                                            "/api/products/**",       // 상품 조회
                                            "/api/brand/**",         // 브랜드 조회 (verify 제외)
                                            "/api/events/**",         // 이벤트 조회
                                            "/api/categories/**",     // 카테고리 조회
                                            "/api/test/**",           // 테스트 API (개발 환경 전용)
                                            "/swagger-ui/**",         // Swagger UI
                                            "/v3/api-docs/**",        // API 문서
                                            "/actuator/**",           // Actuator 모니터링
                                            "/error"                 // 에러 페이지
                                    )
                                    .permitAll()
                                    // 인증이 필요한 경로
                                    .requestMatchers("/api/brand/verify")  // 브랜드 소속 검증
                                    .authenticated()
                                    // 그 외 모든 요청은 인증 필요
                                    .anyRequest()
                                    .authenticated())

            .exceptionHandling(
                    exception ->
                            exception
                                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                                    .accessDeniedHandler(restAccessDeniedHandler))

            .build();
  }


  @Bean
  public CorsFilter corsFilter() {
    return new CorsFilter(corsConfigurationSource());
  }

  @Bean
  public UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOrigin("http://localhost:5173");
    config.addAllowedOrigin("https://sinsang.store");
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
