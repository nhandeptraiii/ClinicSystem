package vn.project.ClinicSystem.controller;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.project.ClinicSystem.model.dto.DiagnosisRequestDto;
import vn.project.ClinicSystem.model.dto.DiagnosisResponseDto;
import vn.project.ClinicSystem.service.DiagnosisAiClient;

/**
 * Cung cấp gợi ý sức khỏe bằng mô hình AI - chỉ mang tính tham khảo, không thay thế chẩn đoán của bác sĩ.
 */
@RestController
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisAiClient diagnosisAiClient;

    @PostMapping("/analyze")
    public ResponseEntity<DiagnosisResponseDto> analyzeSymptoms(
            @RequestBody DiagnosisRequestDto requestDto) {
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ.");
        }
        List<String> sanitizedSymptoms = requestDto.getSymptoms() == null ? List.of()
                : requestDto.getSymptoms().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList());
        if (sanitizedSymptoms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng cung cấp ít nhất một triệu chứng để hệ thống phân tích.");
        }
        requestDto.setSymptoms(sanitizedSymptoms);
        requestDto.setTopK(resolveTopK(requestDto.getTopK()));

        DiagnosisResponseDto response = diagnosisAiClient.predict(requestDto);
        return ResponseEntity.ok(response);
    }

    private int resolveTopK(Integer input) {
        int candidate = input == null ? 5 : input;
        candidate = Math.max(1, candidate);
        candidate = Math.min(25, candidate);
        return candidate;
    }
}
