package com.ryr.ros2cal_api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * Trusted issuer URIs for JWTs (comma-separated in config).
     */
    private List<String> allowedIssuers;

    /**
     * Allowed CORS origins (comma-separated in config).
     */
    private List<String> allowedOrigins;

    public List<String> getAllowedIssuers() {
        return allowedIssuers;
    }

    public void setAllowedIssuers(List<String> allowedIssuers) {
        this.allowedIssuers = allowedIssuers;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
