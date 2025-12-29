package com.ryr.ros2cal_api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.ryr.ros2cal_api.roster.RosterConversionService;
import com.ryr.ros2cal_api.roster.RosterIcsExporter;
import com.ryr.ros2cal_api.roster.RosterParseResult;
import com.ryr.ros2cal_api.roster.RosterProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/roster")
public class RosterController {

    private static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    private final long maxUploadBytes;
    private final RosterConversionService rosterConversionService;
    private final RosterIcsExporter rosterIcsExporter;
    private final RosterProperties rosterProperties;

    public RosterController(
            @Value("${spring.servlet.multipart.max-file-size:" + DEFAULT_MAX_BYTES + "}") long maxUploadBytes,
            RosterConversionService rosterConversionService,
            RosterIcsExporter rosterIcsExporter,
            RosterProperties rosterProperties) {
        this.maxUploadBytes = maxUploadBytes;
        this.rosterConversionService = rosterConversionService;
        this.rosterIcsExporter = rosterIcsExporter;
        this.rosterProperties = rosterProperties;
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertRoster(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "format", required = false) String format) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is required");
        }
        if (image.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image exceeds max size");
        }

        String normalizedFormat = normalizeFormat(format);
        if (!"json".equals(normalizedFormat) && !"ics".equals(normalizedFormat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be JSON or ICS");
        }

        byte[] bytes = readBytes(image);
        if (!isJpegOrPng(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be JPG or PNG");
        }

        if (readImage(bytes) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image content");
        }

        RosterParseResult result = rosterConversionService.parseRoster(bytes);
        if ("ics".equals(normalizedFormat)) {
            String ics = rosterIcsExporter.jsonToIcs(
                    result.getData(),
                    rosterProperties.getCalendarName(),
                    rosterProperties.getLocalTz());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/calendar"))
                    .body(ics);
        }
        return ResponseEntity.ok(result.getData());
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "json";
        }
        return format.trim().toLowerCase();
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unable to read image");
        }
    }

    private Object readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean isJpegOrPng(byte[] bytes) {
        return isJpeg(bytes) || isPng(bytes);
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes != null && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }
}
