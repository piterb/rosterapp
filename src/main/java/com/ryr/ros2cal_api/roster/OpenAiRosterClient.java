package com.ryr.ros2cal_api.roster;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class OpenAiRosterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRosterClient.class);
    private static final int LOG_TEXT_LIMIT = 2000;

    private final OpenAIClient openAIClient;
    private final OpenAiProperties properties;

    public OpenAiRosterClient(OpenAIClient openAIClient, OpenAiProperties properties) {
        this.openAIClient = openAIClient;
        this.properties = properties;
    }

    public OpenAiResult ocrImage(byte[] pngBytes) {
        ensureApiKey();
        String encoded = Base64.getEncoder().encodeToString(pngBytes);
        ResponseInputImage image = ResponseInputImage.builder()
                .imageUrl("data:image/png;base64," + encoded)
                .detail(ResponseInputImage.Detail.HIGH)
                .build();

        ResponseInputItem system = ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.SYSTEM)
                .addInputTextContent(RosterPrompts.SYSTEM_PROMPT_OCR)
                .build());
        ResponseInputItem user = ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addInputTextContent("Transcribe the roster in this image exactly as text.")
                .addContent(image)
                .build());

        ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
                .model(properties.getOcrModel())
                .temperature(0.0)
                .topP(1.0)
                .inputOfResponse(List.of(system, user));
        String cacheKey = applyCacheControl(paramsBuilder);
        ResponseCreateParams params = paramsBuilder.build();
        log.info(
                "OpenAI OCR request model={} cache_key={} system_prompt={} user_prompt={} image_bytes={} image_base64_chars={}",
                properties.getOcrModel(),
                cacheKey,
                truncateText(RosterPrompts.SYSTEM_PROMPT_OCR),
                truncateText("Transcribe the roster in this image exactly as text."),
                pngBytes.length,
                encoded.length());
        Response response = execute(params);
        String outputText = extractOutputText(response);
        log.info("OpenAI OCR response output_chars={} output_text={}", outputText.length(), truncateText(outputText));
        CallUsage usage = extractUsage(response);
        logUsage("OpenAI OCR usage", usage);
        return new OpenAiResult(outputText, usage);
    }

    public OpenAiResult parseRosterText(String rosterText) {
        ensureApiKey();
        ResponseInputItem system = ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.SYSTEM)
                .addInputTextContent(RosterPrompts.SYSTEM_PROMPT_PARSE)
                .build());
        ResponseInputItem user = ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addInputTextContent(rosterText)
                .build());
        ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
                .model(properties.getParseModel())
                .temperature(0.0)
                .topP(1.0)
                .inputOfResponse(List.of(system, user));
        String cacheKey = applyCacheControl(paramsBuilder);
        ResponseCreateParams params = paramsBuilder.build();
        log.info(
                "OpenAI parse request model={} cache_key={} system_prompt={} input_text={}",
                properties.getParseModel(),
                cacheKey,
                truncateText(RosterPrompts.SYSTEM_PROMPT_PARSE),
                truncateText(rosterText));
        Response response = execute(params);
        String outputText = extractOutputText(response);
        log.info("OpenAI parse response output_chars={} output_text={}", outputText.length(), truncateText(outputText));
        CallUsage usage = extractUsage(response);
        logUsage("OpenAI parse usage", usage);
        return new OpenAiResult(outputText, usage);
    }

    private Response execute(ResponseCreateParams params) {
        try {
            return openAIClient.responses().create(params);
        } catch (OpenAIServiceException ex) {
            log.error("OpenAI service error status={} message={}", ex.statusCode(), ex.getMessage());
            HttpStatus status = HttpStatus.resolve(ex.statusCode());
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
            throw new ResponseStatusException(status, ex.getMessage());
        } catch (OpenAIException ex) {
            log.error("OpenAI request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed");
        }
    }

    private String extractOutputText(Response response) {
        StringBuilder out = new StringBuilder();
        response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .forEach(out::append);
        if (out.length() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI response missing output text");
        }
        return out.toString();
    }

    private CallUsage extractUsage(Response response) {
        CallUsage usage = new CallUsage();
        if (response.usage().isEmpty()) {
            return usage;
        }
        var usageNode = response.usage().get();
        usage.setInputTokens(toInt(usageNode._inputTokens().asNumber().orElse(0)));
        usage.setOutputTokens(toInt(usageNode._outputTokens().asNumber().orElse(0)));
        if (usageNode._totalTokens().asNumber().isPresent()) {
            usage.setTotalTokens(toInt(usageNode._totalTokens().asNumber().get()));
        }

        if (usageNode._inputTokensDetails().asKnown().isPresent()) {
            var details = usageNode._inputTokensDetails().asKnown().get();
            usage.setCachedInputTokens(toInt(details._cachedTokens().asNumber().orElse(0)));
        }
        return usage;
    }

    private int toInt(Number number) {
        if (number == null) {
            return 0;
        }
        return number.intValue();
    }

    private void ensureApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OPENAI_API_KEY is not configured");
        }
    }

    public record OpenAiResult(String outputText, CallUsage usage) {}

    private void logUsage(String label, CallUsage usage) {
        if (usage == null) {
            return;
        }
        log.info(
                "{} input_tokens={} output_tokens={} cached_input_tokens={} total_tokens={}",
                label,
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedInputTokens(),
                usage.getEffectiveTotal());
    }

    private String applyCacheControl(ResponseCreateParams.Builder builder) {
        if (!properties.isEnableCache()) {
            String key = UUID.randomUUID().toString();
            builder.promptCacheKey(key);
            log.info("OpenAI cache bypass enabled via prompt_cache_key={}", key);
            return key;
        }
        return "default";
    }

    private String truncateText(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= LOG_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, LOG_TEXT_LIMIT) + "...(truncated)";
    }

}
