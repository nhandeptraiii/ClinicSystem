package vn.project.ClinicSystem.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;
import vn.project.ClinicSystem.model.dto.DiagnosisRequestDto;
import vn.project.ClinicSystem.model.dto.DiagnosisResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DiagnosisAiClient {

    private final RestTemplate restTemplate;
    private final String predictUrl;
    private final String symptomsUrl;
    private final ObjectMapper objectMapper;

    public DiagnosisAiClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.diagnosis.url:http://localhost:8001}") String aiBaseUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .requestFactory(settings -> {
                    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                    factory.setConnectTimeout(Duration.ofSeconds(5));
                    factory.setReadTimeout(Duration.ofSeconds(10));
                    return factory;
                })
                .build();
        this.predictUrl = buildUrl(aiBaseUrl, "/predict");
        this.symptomsUrl = buildUrl(aiBaseUrl, "/symptoms");
        this.objectMapper = objectMapper;
        log.info("Diagnosis AI base URL configured: {}", aiBaseUrl);
    }

    private String buildUrl(String rawBaseUrl, String path) {
        String sanitized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        if (sanitized.isEmpty()) {
            sanitized = "http://localhost:8001";
        }
        if (!sanitized.startsWith("http://") && !sanitized.startsWith("https://")) {
            sanitized = "http://" + sanitized;
        }
        return UriComponentsBuilder.fromHttpUrl(sanitized)
                .path(path)
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
            log.error("AI service returned status {} with body: {}", httpEx.getStatusCode(),
                    httpEx.getResponseBodyAsString());
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

    public List<String> fetchSymptoms() {
        try {
            ResponseEntity<String[]> response = restTemplate.getForEntity(symptomsUrl, String[].class);
            if (response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
            log.warn("AI service /symptoms trả về body rỗng.");
            return Collections.emptyList();
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("AI service /symptoms returned status {} with body: {}", httpEx.getStatusCode(),
                    httpEx.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException ex) {
            log.error("Cannot fetch symptoms from AI service: {}", ex.getMessage());
            return Collections.emptyList();
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
