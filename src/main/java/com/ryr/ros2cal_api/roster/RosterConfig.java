package com.ryr.ros2cal_api.roster;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, RosterProperties.class})
public class RosterConfig {

    @Bean
    OpenAIClient openAiClient(OpenAiProperties properties) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .fromEnv()
                .maxRetries(0);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.apiKey(properties.getApiKey());
        }
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        if (properties.getRequestTimeout() != null) {
            builder.timeout(Timeout.builder().request(properties.getRequestTimeout()).build());
        }
        return builder.build();
    }
}
