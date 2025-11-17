package vn.project.ClinicSystem.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecaptchaVerificationResponse {
    private boolean success;

    @JsonAlias("challenge_ts")
    private String challengeTs;

    private String hostname;

    @JsonAlias("error-codes")
    private List<String> errorCodes = List.of();
}

