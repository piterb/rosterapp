package com.ryr.ros2cal_api.config;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationManagerResolver<HttpServletRequest> resolver = authenticationManagerResolver();

        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
        List<String> issuers = securityProperties.getAllowedIssuers();
        if (issuers == null || issuers.isEmpty()) {
            throw new IllegalStateException("app.security.allowed-issuers must be configured with at least one issuer URI");
        }
        return JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(issuers);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }
}
