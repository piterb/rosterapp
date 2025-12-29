package com.ryr.ros2cal_api.roster;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    /**
     * OpenAI API key.
     */
    private String apiKey;

    /**
     * Base URL for OpenAI Responses API.
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * Model used for OCR vision step.
     */
    private String ocrModel = "gpt-4.1";

    /**
     * Model used for parsing OCR text into JSON.
     */
    private String parseModel = "gpt-5.1";

    /**
     * HTTP request timeout for OpenAI calls.
     */
    private Duration requestTimeout = Duration.ofSeconds(90);

    /**
     * Enable OpenAI prompt cache reuse.
     */
    private boolean enableCache = true;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOcrModel() {
        return ocrModel;
    }

    public void setOcrModel(String ocrModel) {
        this.ocrModel = ocrModel;
    }

    public String getParseModel() {
        return parseModel;
    }

    public void setParseModel(String parseModel) {
        this.parseModel = parseModel;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }
}
