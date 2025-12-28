package com.ryr.ros2cal_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryr.ros2cal_api.roster.RosterConversionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "app.openai.ocr-model=gpt-4.1-2025-04-14",
        "app.openai.parse-model=gpt-5.1-2025-11-13"
})
class RosterOpenAiIntegrationTest {

    @Autowired
    private RosterConversionService rosterConversionService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerOpenAiKey(DynamicPropertyRegistry registry) {
        registry.add("app.openai.api-key", () -> System.getenv("OPENAI_API_KEY"));
    }

    @Test
    @Tag("openai")
    void rosterImageMatchesGoldenJson() throws Exception {
        assumeTrue(isOpenAiIntegrationEnabled(), "OpenAI integration test is disabled");

        byte[] image = readResource("fixtures/roster-openai/roster.jpg");
        JsonNode expected = objectMapper.readTree(readResource("fixtures/roster-openai/roster_new.json"));

        JsonNode actual = objectMapper.valueToTree(rosterConversionService.parseRoster(image).getData());
        assertEquals(expected, actual);
    }

    private boolean isOpenAiIntegrationEnabled() {
        String enabled = System.getProperty("openaiIT");
        if (enabled == null || enabled.isBlank()) {
            enabled = System.getenv("RUN_OPENAI_IT");
        }
        String apiKey = System.getenv("OPENAI_API_KEY");
        return "true".equalsIgnoreCase(enabled) && apiKey != null && !apiKey.isBlank();
    }

    private byte[] readResource(String name) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            return input.readAllBytes();
        }
    }
}
