package vn.project.ClinicSystem.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.dto.LoginDTO;
import vn.project.ClinicSystem.model.dto.ResLoginDTO;
import vn.project.ClinicSystem.service.RevokedTokenService;
import vn.project.ClinicSystem.util.SecurityUtil;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityUtil securityUtil;
    private final RevokedTokenService revokedTokenService;

    public AuthController(AuthenticationManager authenticationManager,
            SecurityUtil securityUtil,
            RevokedTokenService revokedTokenService) {
        this.authenticationManager = authenticationManager;
        this.securityUtil = securityUtil;
        this.revokedTokenService = revokedTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginDTO.getUsername(), loginDTO.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        String accessToken = this.securityUtil.createToken(authentication);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResLoginDTO res = new ResLoginDTO();
        res.setAccessToken(accessToken);

        return ResponseEntity.ok().body(res);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            Instant expiresAt = jwt.getExpiresAt();
            revokedTokenService.revoke(jwt.getTokenValue(), expiresAt != null ? expiresAt : Instant.now());
        }
        SecurityContextHolder.clearContext();
        revokedTokenService.purgeExpired();
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }
}
