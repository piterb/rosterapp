package com.ryr.ros2cal_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryr.ros2cal_api.roster.RosterConversionService;
import com.ryr.ros2cal_api.roster.RosterIcsExporter;
import com.ryr.ros2cal_api.roster.RosterProperties;
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

    @Autowired
    private RosterIcsExporter rosterIcsExporter;

    @Autowired
    private RosterProperties rosterProperties;

    @DynamicPropertySource
    static void registerOpenAiKey(DynamicPropertyRegistry registry) {
        registry.add("app.openai.api-key", () -> System.getenv("OPENAI_API_KEY"));
    }

    @Test
    @Tag("openai")
    void rosterImageMatchesGoldenJson() throws Exception {
        assumeTrue(isOpenAiIntegrationEnabled(), "OpenAI integration test is disabled");

        byte[] image = readResource("fixtures/roster-openai/roster_input.jpg");
        JsonNode expected = objectMapper.readTree(readResource("fixtures/roster-openai/roster_expected.json"));

        var result = rosterConversionService.parseRoster(image);
        JsonNode actual = objectMapper.valueToTree(result.getData());
        assertEquals(expected, actual);

        String expectedIcs = new String(readResource("fixtures/roster-openai/roster_expected.ics"));
        String actualIcs = rosterIcsExporter.jsonToIcs(
                result.getData(),
                rosterProperties.getCalendarName(),
                rosterProperties.getLocalTz());
        assertEquals(normalizeIcs(expectedIcs), normalizeIcs(actualIcs));
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

    private String normalizeIcs(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder out = new StringBuilder();
        for (String line : normalized.split("\n")) {
            String trimmed = rtrim(line);
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("DTSTAMP:")) {
                continue;
            }
            out.append(trimmed).append('\n');
        }
        return out.toString().trim();
    }

    private String rtrim(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
