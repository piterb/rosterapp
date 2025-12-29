package com.ryr.ros2cal_api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import com.ryr.ros2cal_api.roster.CallUsage;
import com.ryr.ros2cal_api.roster.RosterConversionService;
import com.ryr.ros2cal_api.roster.RosterIcsExporter;
import com.ryr.ros2cal_api.roster.RosterParseResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.servlet.multipart.max-file-size=" + RosterControllerTest.MAX_UPLOAD_BYTES,
        "spring.servlet.multipart.max-request-size=" + RosterControllerTest.MAX_UPLOAD_BYTES
})
@AutoConfigureMockMvc
@Import(RosterControllerTest.MockConfig.class)
class RosterControllerTest {

    static final long MAX_UPLOAD_BYTES = 1024;

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        RosterConversionService rosterConversionService() {
            return Mockito.mock(RosterConversionService.class);
        }

        @Bean
        @Primary
        RosterIcsExporter rosterIcsExporter() {
            return Mockito.mock(RosterIcsExporter.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RosterConversionService rosterConversionService;

    @Autowired
    private RosterIcsExporter rosterIcsExporter;

    @Test
    void convertRosterRequiresAuth() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "test.png",
                "image/png",
                createPngBytes());
        mockMvc.perform(multipart("/api/roster/convert").file(image))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void convertRosterReturnsSampleStringWhenAuthenticated() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "test.png",
                "image/png",
                createPngBytes());
        when(rosterConversionService.parseRoster(any())).thenReturn(
                new RosterParseResult(Map.of("events", List.of()), new CallUsage(), new CallUsage()));
        mockMvc.perform(multipart("/api/roster/convert")
                        .file(image)
                        .with(jwt().jwt(jwt -> jwt.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void convertRosterRejectsUnsupportedImageType() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "test.gif",
                "image/gif",
                new byte[] { 'G', 'I', 'F', '8', '9', 'a' });
        mockMvc.perform(multipart("/api/roster/convert")
                        .file(image)
                        .with(jwt().jwt(jwt -> jwt.subject("user-123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertRosterRejectsUnsupportedOutputFormat() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "test.png",
                "image/png",
                createPngBytes());
        mockMvc.perform(multipart("/api/roster/convert")
                        .file(image)
                        .param("format", "xml")
                        .with(jwt().jwt(jwt -> jwt.subject("user-123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertRosterRejectsTooLargePayload() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "big.png",
                "image/png",
                createOversizedPngBytes());
        mockMvc.perform(multipart("/api/roster/convert")
                        .file(image)
                        .with(jwt().jwt(jwt -> jwt.subject("user-123"))))
                .andExpect(status().isBadRequest());
    }

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("unable to write png");
        }
        return out.toByteArray();
    }

    private byte[] createOversizedPngBytes() throws IOException {
        byte[] png = createPngBytes();
        int target = Math.toIntExact(MAX_UPLOAD_BYTES + 1);
        if (png.length >= target) {
            return png;
        }
        return Arrays.copyOf(png, target);
    }
}
