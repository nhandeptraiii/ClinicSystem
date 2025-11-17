package vn.project.ClinicSystem.service;

import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import vn.project.ClinicSystem.service.dto.RecaptchaVerificationResponse;

@Service
public class RecaptchaService {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String secretKey;

    public RecaptchaService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${clinicsystem.recaptcha.enabled:false}") boolean enabled,
            @Value("${clinicsystem.recaptcha.secret-key:}") String secretKey) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.secretKey = secretKey;
        this.enabled = enabled && secretKey != null && !secretKey.isBlank();
    }

    public void validateToken(String token, String remoteIp) {
        if (!enabled) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Vui lòng xác minh reCAPTCHA trước khi gửi yêu cầu.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

            RecaptchaVerificationResponse response = restTemplate.postForObject(
                    VERIFY_URL,
                    request,
                    RecaptchaVerificationResponse.class);

            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException(buildErrorMessage(response));
            }
        } catch (RestClientException ex) {
            throw new IllegalStateException("Không thể xác minh reCAPTCHA. Vui lòng thử lại sau.", ex);
        }
    }

    private String buildErrorMessage(RecaptchaVerificationResponse response) {
        if (response == null || response.getErrorCodes() == null || response.getErrorCodes().isEmpty()) {
            return "Xác minh reCAPTCHA thất bại. Vui lòng thử lại.";
        }
        return "Xác minh reCAPTCHA thất bại: " + String.join(", ",
                response.getErrorCodes().stream()
                        .filter(Objects::nonNull)
                        .toList());
    }
}
