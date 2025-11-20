package vn.project.ClinicSystem.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;
import vn.project.ClinicSystem.model.dto.DiagnosisRequestDto;
import vn.project.ClinicSystem.model.dto.DiagnosisResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class DiagnosisAiClient {

    private final RestTemplate restTemplate;
    private final String predictUrl;
    private final ObjectMapper objectMapper;

    public DiagnosisAiClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.diagnosis.url:http://localhost:8001}") String aiBaseUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.predictUrl = buildPredictUrl(aiBaseUrl);
        this.objectMapper = objectMapper;
        log.info("Diagnosis AI base URL configured: {}", this.predictUrl);
    }

    private String buildPredictUrl(String rawBaseUrl) {
        String sanitized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        if (sanitized.isEmpty()) {
            sanitized = "http://localhost:8001";
        }
        if (!sanitized.startsWith("http://") && !sanitized.startsWith("https://")) {
            sanitized = "http://" + sanitized;
        }
        return UriComponentsBuilder.fromHttpUrl(sanitized)
                .path("/predict")
                .toUriString();
    }

    public DiagnosisResponseDto predict(DiagnosisRequestDto request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String payload = toJson(request);
            HttpEntity<String> httpEntity = new HttpEntity<>(payload, headers);
            log.debug("Sending diagnosis request payload: {}", payload);
            ResponseEntity<DiagnosisResponseDto> response = restTemplate.exchange(
                    predictUrl,
                    HttpMethod.POST,
                    httpEntity,
                    DiagnosisResponseDto.class);
            DiagnosisResponseDto body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("AI service trả về dữ liệu rỗng");
            }
            return body;
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("AI service returned status {} with body: {}", httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Không thể kết nối tới dịch vụ chuẩn đoán AI. Vui lòng thử lại sau.",
                    httpEx);
        } catch (RestClientException ex) {
            log.error("Cannot connect to diagnosis AI service: {}", ex.getMessage());
            throw new IllegalStateException(
                    "Không thể kết nối tới dịch vụ chuẩn đoán AI. Vui lòng thử lại sau.",
                    ex);
        }
    }

    private String toJson(DiagnosisRequestDto request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể chuyển yêu cầu chuẩn đoán thành JSON", e);
        }
    }
}
