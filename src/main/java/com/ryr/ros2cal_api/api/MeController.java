package com.ryr.ros2cal_api.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaims().containsKey("email") ? jwt.getClaimAsString("email") : null;

        if (log.isDebugEnabled()) {
            log.debug("authenticated request: sub={}, email={}", sub, email);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", sub);
        body.put("email", email);
        body.put("iss", jwt.getIssuer().toString());
        body.put("aud", jwt.getAudience());
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null) {
            body.put("exp", expiresAt.toEpochMilli());
        }
        body.put("principal_type", "jwt");
        return ResponseEntity.ok(body);
    }
}
