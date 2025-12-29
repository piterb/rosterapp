package com.ryr.ros2cal_api.roster;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RosterConversionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RosterConversionService.class);
    private final OpenAiRosterClient openAiRosterClient;
    private final RosterImagePreprocessor imagePreprocessor;
    private final ObjectMapper objectMapper;

    public RosterConversionService(
            OpenAiRosterClient openAiRosterClient,
            RosterImagePreprocessor imagePreprocessor,
            ObjectMapper objectMapper) {
        this.openAiRosterClient = openAiRosterClient;
        this.imagePreprocessor = imagePreprocessor;
        this.objectMapper = objectMapper;
    }

    public RosterParseResult parseRoster(byte[] imageBytes) {
        log.info("Roster parse start image_bytes={}", imageBytes != null ? imageBytes.length : 0);
        byte[] pngBytes;
        try {
            pngBytes = imagePreprocessor.preparePng(imageBytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image content");
        }

        log.info("Roster OCR call start png_bytes={}", pngBytes.length);
        OpenAiRosterClient.OpenAiResult ocr = openAiRosterClient.ocrImage(pngBytes);
        log.info("Roster OCR call done output_chars={}", ocr.outputText() != null ? ocr.outputText().length() : 0);
        log.info("Roster parse call start");
        OpenAiRosterClient.OpenAiResult parsed = openAiRosterClient.parseRosterText(ocr.outputText());
        log.info("Roster parse call done output_chars={}", parsed.outputText() != null ? parsed.outputText().length() : 0);
        Map<String, Object> data;
        try {
            data = objectMapper.readValue(parsed.outputText(), new TypeReference<>() {});
        } catch (IOException ex) {
            log.error("Roster parse JSON decode failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI response was not valid JSON");
        }
        log.info("Roster parse end events_count={}", data.getOrDefault("events", java.util.List.of()) instanceof java.util.List<?> events ? events.size() : 0);
        return new RosterParseResult(data, ocr.usage(), parsed.usage());
    }
}
